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
        ExceptionInfo exception) {

    public enum Status {
        OK,
        EXCEPTION,
        TIMEOUT
    }

    /** The output body, classified for rendering (R12). {@code inline} is capped; overflow is downloadable. */
    public record BodyView(
            BodyType type,
            String contentType,
            long size,
            String inline,
            boolean truncated) {}

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
