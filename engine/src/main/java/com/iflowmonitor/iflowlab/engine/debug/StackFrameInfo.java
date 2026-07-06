package com.iflowmonitor.iflowlab.engine.debug;

import java.util.LinkedHashMap;
import java.util.Map;

/** An immutable snapshot of one call-stack frame at a pause (name, current line, locals). */
public record StackFrameInfo(String name, int line, Map<String, Object> locals) {
    public StackFrameInfo {
        locals = locals == null ? Map.of() : new LinkedHashMap<>(locals);
    }
}
