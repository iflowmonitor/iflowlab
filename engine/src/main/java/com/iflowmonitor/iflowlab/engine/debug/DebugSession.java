package com.iflowmonitor.iflowlab.engine.debug;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    // Data breakpoints ("watches"): a watched local's name mapped to its stop
    // condition. An empty condition means "break on any value change"; otherwise
    // the condition is "<op> <value>" (op in == != > < >= <= contains) and the
    // watch fires on the false→true edge of that predicate. All three maps are
    // touched only under {@link #lock} (onStatement holds it; setWatches acquires
    // it). lastWatchValues holds the last-seen string form of each change-mode
    // local; lastConditionState holds the last predicate result of each
    // condition-mode local, so an edge is one statement-boundary comparison.
    private final Map<String, String> watches = new LinkedHashMap<>();
    private final Map<String, String> lastWatchValues = new HashMap<>();
    private final Map<String, Boolean> lastConditionState = new HashMap<>();

    // Pending local-variable overrides set from the paused UI (DAP setVariable).
    // Consumed one-shot by the instrumentation's applyOverride call right after the
    // next statement hook, so a value the user typed takes effect for the rest of
    // the run. Guarded by {@link #lock}.
    private final Map<String, String> pendingOverrides = new HashMap<>();

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

    /**
     * The locals whose change/condition should pause the run (data breakpoints).
     * Each entry maps a local name to its condition: empty = break on any change;
     * otherwise "&lt;op&gt; &lt;value&gt;" for a false→true predicate edge.
     */
    public void setWatches(Map<String, String> specs) {
        lock.lock();
        try {
            watches.clear();
            watches.putAll(specs);
            // Drop baselines for names no longer watched; kept names keep theirs.
            lastWatchValues.keySet().retainAll(specs.keySet());
            lastConditionState.keySet().retainAll(specs.keySet());
        } finally {
            lock.unlock();
        }
    }

    /** Queue a new value for a local, applied at the next statement (DAP setVariable). */
    public void setLocalOverride(String name, String value) {
        lock.lock();
        try {
            pendingOverrides.put(name, value);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Consume a pending override for {@code name}, coerced to {@code current}'s
     * runtime type; returns {@code current} unchanged when there is none. Called by
     * injected instrumentation on the script thread, once per statement per local.
     * Only string/number/boolean/char locals are editable — a complex object or
     * null is left as-is so a mistyped value can never break the running script.
     */
    public Object applyOverride(String name, Object current) {
        lock.lock();
        try {
            if (!pendingOverrides.containsKey(name)) {
                return current;
            }
            return coerceOverride(pendingOverrides.remove(name), current);
        } finally {
            lock.unlock();
        }
    }

    private static Object coerceOverride(String raw, Object current) {
        if (raw == null || current == null) {
            return current;
        }
        try {
            if (current instanceof Integer) return Integer.valueOf(raw.trim());
            if (current instanceof Long) return Long.valueOf(raw.trim());
            if (current instanceof Short) return Short.valueOf(raw.trim());
            if (current instanceof Byte) return Byte.valueOf(raw.trim());
            if (current instanceof java.math.BigInteger) return new java.math.BigInteger(raw.trim());
            if (current instanceof java.math.BigDecimal) return new java.math.BigDecimal(raw.trim());
            if (current instanceof Double) return Double.valueOf(raw.trim());
            if (current instanceof Float) return Float.valueOf(raw.trim());
            if (current instanceof Boolean) return Boolean.valueOf(raw.trim());
            if (current instanceof Character) return raw.isEmpty() ? current : raw.charAt(0);
            if (current instanceof CharSequence) return raw;
        } catch (RuntimeException e) {
            return current; // best-effort: leave the local unchanged on a bad value
        }
        return current; // complex object → don't risk a type mismatch
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

            // Always re-baseline watched locals (side effect), so a change/edge is
            // seen exactly once regardless of why we stop this statement.
            String dataHit = checkWatches(stack);

            boolean stop = true;
            if (dataHit != null) {
                stopReason = "data breakpoint";
                stopDetail = dataHit; // human phrase, e.g. "x changed" or "x >= 3"
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
     * Called after a mutating statement executes (post-hook). Evaluates only data
     * breakpoints against the just-produced locals and pauses on the current line
     * if one fires — so a change/condition stops on the line that caused it, with
     * the variable still in scope. Line breakpoints and stepping are handled solely
     * by {@link #onStatement} (the pre-hook), so this never interferes with them.
     */
    public void onDataWatch(int line, int depth, List<StackFrameInfo> stack) {
        lock.lock();
        try {
            if (cancelled) {
                throw new DebugCancelledException();
            }
            currentLine = line;
            currentDepth = depth;
            currentStack = stack;
            String dataHit = checkWatches(stack);
            if (dataHit != null) {
                stopReason = "data breakpoint";
                stopDetail = dataHit;
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
     * Re-baselines every watched local and returns a human phrase for one that
     * hit this statement, or null. Two modes per watch:
     * <ul>
     *   <li><b>change</b> (empty condition): fires when the string value differs
     *       from the previous statement. A watch only fires once it has a prior
     *       value (the first sighting sets the baseline), so a variable's initial
     *       assignment is not treated as a change. Phrase: {@code "name changed"}.
     *   <li><b>condition</b> ("&lt;op&gt; &lt;value&gt;"): fires on the false→true
     *       edge of the predicate — the first statement where it holds after not
     *       holding — and re-arms when it goes false again. Phrase:
     *       {@code "name op value"}.
     * </ul>
     * Only the innermost frame is inspected — a data breakpoint is scoped to the
     * current frame, matching how it was set from the paused Variables view.
     * Called under {@link #lock}.
     */
    private String checkWatches(List<StackFrameInfo> stack) {
        if (watches.isEmpty() || stack.isEmpty()) {
            return null;
        }
        Map<String, Object> locals = stack.get(0).locals();
        String hit = null;
        for (Map.Entry<String, String> watch : watches.entrySet()) {
            String name = watch.getKey();
            if (!locals.containsKey(name)) {
                continue;
            }
            Object value = locals.get(name);
            String condition = watch.getValue();
            if (condition == null || condition.isBlank()) {
                String now = String.valueOf(value);
                String prev = lastWatchValues.put(name, now);
                if (prev != null && !prev.equals(now) && hit == null) {
                    hit = name + " changed";
                }
            } else {
                boolean now = evaluateCondition(value, condition);
                Boolean prev = lastConditionState.put(name, now);
                if (now && !Boolean.TRUE.equals(prev) && hit == null) {
                    hit = name + " " + condition;
                }
            }
        }
        return hit;
    }

    /**
     * Evaluates a {@code "<op> <value>"} predicate against a local's current value
     * in pure Java (no user code runs). Numeric operators coerce both sides to
     * {@link BigDecimal}; a non-numeric actual makes an ordering comparison false.
     * Equality falls back to a string compare when either side is non-numeric;
     * {@code contains} is a substring test on the value's string form. A
     * malformed condition evaluates to false.
     */
    static boolean evaluateCondition(Object value, String condition) {
        String trimmed = condition.trim();
        int sp = trimmed.indexOf(' ');
        String op = sp < 0 ? trimmed : trimmed.substring(0, sp);
        String operand = sp < 0 ? "" : trimmed.substring(sp + 1).trim();
        String actual = String.valueOf(value);
        switch (op) {
            case "contains":
                return actual.contains(operand);
            case "==":
            case "!=": {
                BigDecimal a = toNumber(value);
                BigDecimal b = toNumber(operand);
                boolean equal = (a != null && b != null) ? a.compareTo(b) == 0 : actual.equals(operand);
                return op.equals("==") == equal;
            }
            case ">":
            case "<":
            case ">=":
            case "<=": {
                BigDecimal a = toNumber(value);
                BigDecimal b = toNumber(operand);
                if (a == null || b == null) {
                    return false;
                }
                int c = a.compareTo(b);
                return switch (op) {
                    case ">" -> c > 0;
                    case "<" -> c < 0;
                    case ">=" -> c >= 0;
                    default -> c <= 0;
                };
            }
            default:
                return false;
        }
    }

    private static BigDecimal toNumber(Object value) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
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
