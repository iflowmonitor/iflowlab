package com.iflowmonitor.iflowlab.engine.debug;

import java.util.List;
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

    private boolean paused;
    private boolean cancelled;
    private boolean finished;
    private Throwable exitCause;
    private Step stepMode = Step.NONE;
    private int stepAtDepth;
    private int currentLine;
    private int currentDepth;
    private List<StackFrameInfo> currentStack = List.of();

    public void setBreakpoints(Set<Integer> lines) {
        breakpoints.clear();
        breakpoints.addAll(lines);
    }

    public void addBreakpoint(int line) {
        breakpoints.add(line);
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

            boolean stop = breakpoints.contains(line);
            if (!stop) {
                stop = switch (stepMode) {
                    case INTO -> true;
                    case OVER -> depth <= stepAtDepth;
                    case OUT -> depth < stepAtDepth;
                    case NONE -> false;
                };
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
