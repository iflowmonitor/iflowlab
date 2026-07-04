package com.iflowlab.spike.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coordinates a single instrumented script run between the <b>script thread</b>
 * (which calls {@link #onStatement}) and a <b>driver thread</b> (which observes
 * pauses and issues resume / step / cancel commands).
 *
 * <p>All state is guarded by {@link #lock}. The script thread parks on
 * {@link #resumeSignal}; the driver waits on {@link #pausedSignal}.
 */
public final class DebugSession {

    private enum Step { NONE, OVER, INTO }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition pausedSignal = lock.newCondition();   // driver waits here
    private final Condition resumeSignal = lock.newCondition();   // script waits here

    private final Set<Integer> breakpoints = ConcurrentHashMap.newKeySet();

    // --- guarded by lock ---
    private boolean paused;
    private boolean cancelled;
    private boolean finished;
    private Throwable exitCause;
    private Step stepMode = Step.NONE;
    private int stepAtDepth;
    private Frame currentFrame;

    /** Full ordered trace of every statement hook (for P1 evidence & overhead). */
    private final List<Frame> trace = new ArrayList<>();
    private final boolean recordTrace;

    public DebugSession(boolean recordTrace) {
        this.recordTrace = recordTrace;
    }

    public void addBreakpoint(int line) {
        breakpoints.add(line);
    }

    // ==================================================================
    // Script-thread side
    // ==================================================================

    /** Invoked by injected instrumentation before every original statement. */
    public void onStatement(int line, int depth, Map<String, Object> locals) {
        lock.lock();
        try {
            if (cancelled) {
                throw new DebugCancelledException();
            }
            Frame frame = new Frame(line, depth, locals);
            currentFrame = frame;
            if (recordTrace) {
                trace.add(frame);
            }

            boolean stop = breakpoints.contains(line);
            if (!stop) {
                switch (stepMode) {
                    case INTO -> stop = true;
                    case OVER -> stop = depth <= stepAtDepth;
                    default -> { /* running freely */ }
                }
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

    /** Invoked by the engine when the script thread exits (normally or not). */
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

    // ==================================================================
    // Driver-thread side
    // ==================================================================

    /**
     * Block until the script parks at a breakpoint/step or the run finishes.
     * @return true if the script is now paused, false if it finished first.
     */
    public boolean awaitPause(long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = unit.toNanos(timeout);
        lock.lock();
        try {
            while (!paused && !finished) {
                if (deadlineNanos <= 0) {
                    return false;
                }
                deadlineNanos = pausedSignal.awaitNanos(deadlineNanos);
            }
            return paused;
        } finally {
            lock.unlock();
        }
    }

    public boolean isPaused() {
        lock.lock();
        try {
            return paused;
        } finally {
            lock.unlock();
        }
    }

    public boolean isFinished() {
        lock.lock();
        try {
            return finished;
        } finally {
            lock.unlock();
        }
    }

    public Throwable exitCause() {
        lock.lock();
        try {
            return exitCause;
        } finally {
            lock.unlock();
        }
    }

    /** Current frame (valid while paused). */
    public Frame frame() {
        lock.lock();
        try {
            return currentFrame;
        } finally {
            lock.unlock();
        }
    }

    public void resume() {
        release(Step.NONE, 0);
    }

    public void stepOver() {
        Frame f = frame();
        release(Step.OVER, f == null ? 0 : f.depth);
    }

    public void stepInto() {
        release(Step.INTO, 0);
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

    /** Cancel the run; a parked or looping script unwinds via DebugCancelledException. */
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

    public List<Frame> trace() {
        lock.lock();
        try {
            return new ArrayList<>(trace);
        } finally {
            lock.unlock();
        }
    }
}
