package com.iflowmonitor.iflowlab.engine.debug;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coordinates one instrumented run between the <b>script thread</b> (which calls
 * {@link #onStatement}) and a <b>driver thread</b> (which observes pauses and
 * issues resume/step/cancel). All mutable state is guarded by {@link #lock}.
 */
public final class DebugSession {

    private enum Step { NONE, OVER, INTO, OUT }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition pausedSignal = lock.newCondition();
    private final Condition resumeSignal = lock.newCondition();

    private final Set<Integer> breakpoints = ConcurrentHashMap.newKeySet();

    // Data breakpoints ("watches"): names of locals to stop on when their value
    // changes. Both maps are touched only under {@link #lock} (onStatement holds
    // it; setWatches acquires it). lastWatchValues holds the last-seen string form
    // of each watched local, so a change is one statement-boundary comparison.
    private final Set<String> watches = new HashSet<>();
    private final Map<String, String> lastWatchValues = new HashMap<>();

    private boolean paused;
    private boolean cancelled;
    private boolean finished;
    private Throwable exitCause;
    private Step stepMode = Step.NONE;
    private int stepAtDepth;
    private int currentLine;
    private int currentDepth;
    private List<StackFrameInfo> currentStack = List.of();
    private String stopReason = "";
    private String stopDetail = "";

    public void setBreakpoints(Set<Integer> lines) {
        breakpoints.clear();
        breakpoints.addAll(lines);
    }

    public void addBreakpoint(int line) {
        breakpoints.add(line);
    }

    /** The locals whose value changes should pause the run (data breakpoints). */
    public void setWatches(Set<String> names) {
        lock.lock();
        try {
            watches.clear();
            watches.addAll(names);
            // Drop baselines for names no longer watched; kept names keep theirs.
            lastWatchValues.keySet().retainAll(names);
        } finally {
            lock.unlock();
        }
    }

    // ---- script-thread side ----

    public void onStatement(int line, int depth, List<StackFrameInfo> stack) {
        lock.lock();
        try {
            if (cancelled) {
                throw new DebugCancelledException();
            }
            currentLine = line;
            currentDepth = depth;
            currentStack = stack;

            // Always re-baseline watched locals (side effect), so a change is seen
            // exactly once regardless of why we stop this statement.
            String dataHit = checkWatches(stack);

            boolean stop = true;
            if (dataHit != null) {
                stopReason = "data breakpoint";
                stopDetail = dataHit;
            } else if (breakpoints.contains(line)) {
                stopReason = "breakpoint";
                stopDetail = "";
            } else if (switch (stepMode) {
                case INTO -> true;
                case OVER -> depth <= stepAtDepth;
                case OUT -> depth < stepAtDepth;
                case NONE -> false;
            }) {
                stopReason = "step";
                stopDetail = "";
            } else {
                stop = false;
            }

            if (stop) {
                paused = true;
                stepMode = Step.NONE;
                pausedSignal.signalAll();
                while (paused && !cancelled) {
                    resumeSignal.awaitUninterruptibly();
                }
                if (cancelled) {
                    throw new DebugCancelledException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates the baseline for every watched local and returns the name of one
     * whose value changed since the previous statement, or null. A watch only
     * fires once it has a prior value (the first sighting sets the baseline), so
     * a variable's initial assignment is not treated as a change. Only the
     * innermost frame is inspected — a data breakpoint is scoped to the current
     * frame, matching how it was set from the paused Variables view. Called under
     * {@link #lock}.
     */
    private String checkWatches(List<StackFrameInfo> stack) {
        if (watches.isEmpty() || stack.isEmpty()) {
            return null;
        }
        Map<String, Object> locals = stack.get(0).locals();
        String changed = null;
        for (String name : watches) {
            if (!locals.containsKey(name)) {
                continue;
            }
            String now = String.valueOf(locals.get(name));
            String prev = lastWatchValues.put(name, now);
            if (prev != null && !prev.equals(now)) {
                changed = name;
            }
        }
        return changed;
    }

    public void markFinished(Throwable cause) {
        lock.lock();
        try {
            finished = true;
            exitCause = cause;
            paused = false;
            pausedSignal.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // ---- driver-thread side ----

    /** @return true if now paused, false if the run finished first. */
    public boolean awaitPause(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = unit.toNanos(timeout);
        lock.lock();
        try {
            while (!paused && !finished) {
                if (deadline <= 0) {
                    return false;
                }
                deadline = pausedSignal.awaitNanos(deadline);
            }
            return paused;
        } finally {
            lock.unlock();
        }
    }

    public boolean isPaused() {
        return guarded(() -> paused);
    }

    public boolean isFinished() {
        return guarded(() -> finished);
    }

    public Throwable exitCause() {
        return guarded(() -> exitCause);
    }

    public int currentLine() {
        return guarded(() -> currentLine);
    }

    /** Why the run last paused: "breakpoint", "step", or "data breakpoint". */
    public String stopReason() {
        return guarded(() -> stopReason);
    }

    /** For a data breakpoint pause, the name of the local that changed (else ""). */
    public String stopDetail() {
        return guarded(() -> stopDetail);
    }

    public List<StackFrameInfo> stack() {
        return guarded(() -> currentStack);
    }

    public void resume() {
        release(Step.NONE, 0);
    }

    public void stepOver() {
        release(Step.OVER, currentDepthLocked());
    }

    public void stepInto() {
        release(Step.INTO, 0);
    }

    public void stepOut() {
        release(Step.OUT, currentDepthLocked());
    }

    public void cancel() {
        lock.lock();
        try {
            cancelled = true;
            paused = false;
            resumeSignal.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private int currentDepthLocked() {
        return guarded(() -> currentDepth);
    }

    private void release(Step mode, int atDepth) {
        lock.lock();
        try {
            stepMode = mode;
            stepAtDepth = atDepth;
            paused = false;
            resumeSignal.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private <T> T guarded(java.util.function.Supplier<T> body) {
        lock.lock();
        try {
            return body.get();
        } finally {
            lock.unlock();
        }
    }
}
