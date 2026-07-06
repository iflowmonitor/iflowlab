package com.iflowmonitor.iflowlab.cpimock;

/** One captured MessageLog interaction, surfaced in the workbench log pane (D6). */
public record LogEntry(Kind kind, String name, String value, String mediaType) {

    public enum Kind {
        STRING_PROPERTY,
        ATTACHMENT,
        CUSTOM_HEADER
    }

    static LogEntry stringProperty(String name, String value) {
        return new LogEntry(Kind.STRING_PROPERTY, name, value, null);
    }

    static LogEntry attachment(String name, String payload, String mediaType) {
        return new LogEntry(Kind.ATTACHMENT, name, payload, mediaType);
    }

    static LogEntry customHeader(String name, String value) {
        return new LogEntry(Kind.CUSTOM_HEADER, name, value, null);
    }
}
