package com.iflowmonitor.iflowlab.engine;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One workbench run: a script plus the input Message to feed it. Engine-agnostic
 * (D2) — the same request drives the plain run engine now and the debug engine later.
 */
public record RunRequest(
        String script,
        byte[] body,
        String contentType,
        Map<String, Object> headers,
        Map<String, Object> properties,
        List<java.net.URL> extraClasspath,
        long timeoutMs) {

    public RunRequest {
        headers = headers == null ? Map.of() : new LinkedHashMap<>(headers);
        properties = properties == null ? Map.of() : new LinkedHashMap<>(properties);
        extraClasspath = extraClasspath == null ? List.of() : List.copyOf(extraClasspath);
        if (timeoutMs <= 0) {
            timeoutMs = 10_000L;
        }
    }

    /** Convenience for tests: a text body, no headers/properties, default timeout. */
    public static RunRequest ofScriptAndText(String script, String body) {
        return new RunRequest(
                script,
                body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                "text/plain",
                Map.of(),
                Map.of(),
                List.of(),
                10_000L);
    }
}
