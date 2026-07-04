package com.iflowlab.spike.debug;

import java.util.Map;

/**
 * Static entry point that injected instrumentation calls into. Bound to the
 * current {@link DebugSession} per-thread so the hook works identically inside
 * top-level script code, methods, and closures (a plain static call needs no
 * lexical access to the session).
 */
public final class DebugRuntime {

    private static final ThreadLocal<DebugSession> CURRENT = new ThreadLocal<>();

    private DebugRuntime() {
    }

    public static void bind(DebugSession session) {
        CURRENT.set(session);
    }

    public static void unbind() {
        CURRENT.remove();
    }

    /**
     * Called before every original statement. {@code line} is the source line,
     * {@code depth} the lexical scope depth, {@code locals} a live snapshot of
     * in-scope local variables (may be empty).
     */
    public static void onStatement(int line, int depth, Map<String, Object> locals) {
        DebugSession session = CURRENT.get();
        if (session != null) {
            session.onStatement(line, depth, locals);
        }
    }
}
