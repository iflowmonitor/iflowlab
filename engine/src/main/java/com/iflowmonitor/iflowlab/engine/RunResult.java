package com.iflowmonitor.iflowlab.engine;

import java.util.List;
import java.util.Map;

/**
 * The outcome of a run — the envelope the {@code POST /run} endpoint serialises (R12).
 */
public record RunResult(
        Status status,
        BodyView body,
        Map<String, Object> headersBefore,
        Map<String, Object> headersAfter,
        Map<String, Object> propertiesBefore,
        Map<String, Object> propertiesAfter,
        List<LogLine> logs,
        List<AttachmentView> attachments,
        ExceptionInfo exception) {

    /** Back-compat: a result with no attachments. */
    public RunResult(
            Status status,
            BodyView body,
            Map<String, Object> headersBefore,
            Map<String, Object> headersAfter,
            Map<String, Object> propertiesBefore,
            Map<String, Object> propertiesAfter,
            List<LogLine> logs,
            ExceptionInfo exception) {
        this(status, body, headersBefore, headersAfter, propertiesBefore, propertiesAfter, logs, List.of(), exception);
    }

    public enum Status {
        OK,
        EXCEPTION,
        TIMEOUT
    }

    /** An attachment the script left on the message — surfaced after a run (slice 8). */
    public record AttachmentView(String name, String contentType, long size, String inline, boolean truncated) {}

    /**
     * The output body, classified for rendering (R12). {@code inline} is capped for
     * display; {@code downloadBase64} carries the full bytes for a faithful download
     * when the display form isn't enough (binary, or a truncated body) — null when
     * the client can reconstruct the file from {@code inline} (untruncated text) or
     * the body is too large to embed.
     */
    public record BodyView(
            BodyType type,
            String contentType,
            long size,
            String inline,
            boolean truncated,
            String downloadBase64) {

        /** Back-compat: no separate downloadable body (client uses {@code inline}). */
        public BodyView(BodyType type, String contentType, long size, String inline, boolean truncated) {
            this(type, contentType, size, inline, truncated, null);
        }
    }

    public enum BodyType {
        XML,
        JSON,
        TEXT,
        BINARY
    }

    /** One captured log line — {@code println} or a MessageLog interaction (D6). */
    public record LogLine(String level, String source, String message) {}

    /** A thrown-and-uncaught error, mapped back to the script line where possible (D6). */
    public record ExceptionInfo(String type, String message, Integer mappedLine, String stackTrace) {}
}
