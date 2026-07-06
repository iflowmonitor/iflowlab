package com.iflowmonitor.iflowlab.engine.debug;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A snapshot captured when a statement hook fires: the source line, the effective
 * scope depth (runtime call depth + lexical closure nesting — used for stepping),
 * and the locals visible at that point (re-read live at hook time).
 */
public final class Frame {

    public final int line;
    public final int depth;
    private final Map<String, Object> locals;

    public Frame(int line, int depth, Map<String, Object> locals) {
        this.line = line;
        this.depth = depth;
        this.locals = locals == null ? Collections.emptyMap() : new LinkedHashMap<>(locals);
    }

    public Map<String, Object> locals() {
        return Collections.unmodifiableMap(locals);
    }

    @Override
    public String toString() {
        return "Frame{line=" + line + ", depth=" + depth + ", locals=" + locals + '}';
    }
}
