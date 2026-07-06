package com.iflowmonitor.iflowlab.app;

import com.iflowmonitor.iflowlab.cpimock.services.CpiServices;
import com.iflowmonitor.iflowlab.engine.AttachmentInput;
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
        Long timeoutMs,
        String kind,
        List<AttachmentDto> attachments) {

    public record AttachmentDto(String name, String body, String contentType) {}

    RunRequest toRunRequest() {
        return toRunRequest(null);
    }

    RunRequest toRunRequest(CpiServices services) {
        List<AttachmentInput> atts = attachments == null ? List.of() : attachments.stream()
                .filter(a -> a.name() != null && !a.name().isBlank())
                .map(a -> new AttachmentInput(
                        a.name(),
                        a.body() == null ? new byte[0] : a.body().getBytes(StandardCharsets.UTF_8),
                        a.contentType()))
                .toList();
        return new RunRequest(
                script,
                body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                contentType,
                headers,
                properties,
                List.of(),
                timeoutMs == null ? 0L : timeoutMs,
                services,
                atts);
    }
}
