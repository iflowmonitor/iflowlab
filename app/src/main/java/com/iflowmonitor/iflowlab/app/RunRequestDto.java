package com.iflowmonitor.iflowlab.app;

import com.iflowmonitor.iflowlab.engine.RunRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The JSON body the SPA posts to {@code /run}. Body arrives as text (slice 1);
 * binary input arrives later. Maps to the engine's {@link RunRequest}.
 */
public record RunRequestDto(
        String script,
        String body,
        String contentType,
        Map<String, Object> headers,
        Map<String, Object> properties,
        Long timeoutMs) {

    RunRequest toRunRequest() {
        return new RunRequest(
                script,
                body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                contentType,
                headers,
                properties,
                List.of(),
                timeoutMs == null ? 0L : timeoutMs);
    }
}
