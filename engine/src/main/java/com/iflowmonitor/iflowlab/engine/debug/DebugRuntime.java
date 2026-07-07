package com.iflowmonitor.iflowlab.engine.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Static entry point the injected instrumentation calls into, bound to the
 * current {@link DebugSession} per thread.
 *
 * <p>Maintains a per-thread <b>runtime call stack</b> of script-method frames
 * ({@link #enterMethod}/{@link #exitMethod}). The effective stepping depth is
 * {@code callStackSize + lexicalClosureDepth}, so step-over/step-out work
 * correctly across method calls — the fix for the P4 limitation that static
 * lexical depth alone could not see a dynamic call frame.
 */
public final class DebugRuntime {

    private static final ThreadLocal<DebugSession> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Deque<MethodFrame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private DebugRuntime() {}

    public static void bind(DebugSession session) {
        CURRENT.set(session);
        STACK.get().clear();
    }

    public static void unbind() {
        CURRENT.remove();
        STACK.remove();
    }

    /** Injected at the start of every instrumented method body. */
    public static void enterMethod(String name) {
        STACK.get().push(new MethodFrame(name));
    }

    /** Injected in a finally at the end of every instrumented method body. */
    public static void exitMethod() {
        Deque<MethodFrame> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /**
     * Called before every original statement. {@code lexicalDepth} is the static
     * closure-nesting depth within the current method (0 at method top level).
     */
    public static void onStatement(int line, int lexicalDepth, Map<String, Object> locals) {
        DebugSession session = CURRENT.get();
        if (session == null) {
            return;
        }
        session.onStatement(line, effectiveDepth(lexicalDepth), snapshot(line, locals));
    }

    /**
     * Called <em>after</em> every mutating statement, with the locals it left in
     * scope. Only data breakpoints are evaluated here (not line breakpoints or
     * stepping), so a watched value's change is caught on the very line that
     * caused it — while the variable is still in scope — rather than one statement
     * later (which may already be out of the declaring block).
     */
    public static void afterStatement(int line, int lexicalDepth, Map<String, Object> locals) {
        DebugSession session = CURRENT.get();
        if (session == null) {
            return;
        }
        session.onDataWatch(line, effectiveDepth(lexicalDepth), snapshot(line, locals));
    }

    /**
     * Injected right after each statement hook, once per visible local:
     * {@code x = applyOverride('x', x)}. Returns the debugger-set value (coerced)
     * when one is pending for {@code name}, otherwise {@code current} unchanged.
     */
    public static Object applyOverride(String name, Object current) {
        DebugSession session = CURRENT.get();
        return session == null ? current : session.applyOverride(name, current);
    }

    private static int effectiveDepth(int lexicalDepth) {
        return STACK.get().size() + lexicalDepth;
    }

    private static List<StackFrameInfo> snapshot(int line, Map<String, Object> locals) {
        Deque<MethodFrame> stack = STACK.get();
        MethodFrame top = stack.peek();
        if (top != null) {
            top.line = line;
            top.locals = locals;
        }
        List<StackFrameInfo> snapshot = new ArrayList<>(stack.size());
        for (MethodFrame f : stack) { // Deque iterates head→tail = innermost→outermost
            snapshot.add(new StackFrameInfo(f.name, f.line, f.locals));
        }
        return snapshot;
    }

    private static final class MethodFrame {
        final String name;
        int line;
        Map<String, Object> locals = Map.of();

        MethodFrame(String name) {
            this.name = name;
        }
    }
}
