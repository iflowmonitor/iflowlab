package com.iflowmonitor.iflowlab.app;

import com.iflowmonitor.iflowlab.cpimock.services.CpiServices;
import com.iflowmonitor.iflowlab.engine.AttachmentInput;
import com.iflowmonitor.iflowlab.engine.RunRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * The JSON body the SPA posts to {@code /run}. A text body arrives in
 * {@code body}; a binary body (zip/pdf/xlsx uploaded in the workbench) arrives
 * base64-encoded in {@code bodyBase64} and wins when present. {@code function}
 * names the Groovy entry method (CPI's default is {@code processData}). Maps to
 * the engine's {@link RunRequest}.
 */
public record RunRequestDto(
        String script,
        String body,
        String bodyBase64,
        String contentType,
        Map<String, Object> headers,
        Map<String, Object> properties,
        Long timeoutMs,
        String kind,
        List<AttachmentDto> attachments,
        String function,
        ServicesDto services) {

    /** A binary attachment rides base64 in {@code bodyBase64}; text may use {@code body}. */
    public record AttachmentDto(String name, String body, String bodyBase64, String contentType) {}

    /** Back-compat: text-only request without inline services (local product). */
    public RunRequestDto(
            String script, String body, String contentType, Map<String, Object> headers,
            Map<String, Object> properties, Long timeoutMs, String kind, List<AttachmentDto> attachments) {
        this(script, body, null, contentType, headers, properties, timeoutMs, kind, attachments, null, null);
    }

    /** Back-compat: text-only request with inline services (pre-binary/function shape). */
    public RunRequestDto(
            String script, String body, String contentType, Map<String, Object> headers,
            Map<String, Object> properties, Long timeoutMs, String kind,
            List<AttachmentDto> attachments, ServicesDto services) {
        this(script, body, null, contentType, headers, properties, timeoutMs, kind, attachments, null, services);
    }

    RunRequest toRunRequest() {
        return toRunRequest(null);
    }

    /** Inline services (stateless runner) take precedence over the workspace fallback. */
    RunRequest toRunRequest(CpiServices fallback) {
        CpiServices effective = services != null ? services.toCpiServices() : fallback;
        List<AttachmentInput> atts = attachments == null ? List.of() : attachments.stream()
                .filter(a -> a.name() != null && !a.name().isBlank())
                .map(a -> new AttachmentInput(a.name(), attachmentBytes(a), a.contentType()))
                .toList();
        return new RunRequest(
                script,
                bodyBytes(),
                contentType,
                headers,
                properties,
                List.of(),
                timeoutMs == null ? 0L : timeoutMs,
                effective,
                atts,
                function);
    }

    /** The input body as bytes: decoded base64 when a binary body was uploaded, else UTF-8 text. */
    private byte[] bodyBytes() {
        if (bodyBase64 != null && !bodyBase64.isBlank()) {
            return Base64.getDecoder().decode(bodyBase64.trim());
        }
        return body == null ? null : body.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] attachmentBytes(AttachmentDto a) {
        if (a.bodyBase64() != null && !a.bodyBase64().isBlank()) {
            return Base64.getDecoder().decode(a.bodyBase64().trim());
        }
        return a.body() == null ? new byte[0] : a.body().getBytes(StandardCharsets.UTF_8);
    }
}
