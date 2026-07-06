package com.iflowmonitor.iflowlab.engine.assertions;

/**
 * One expectation about a {@link com.iflowmonitor.iflowlab.engine.RunResult}. A flat
 * (kind, target, expected) shape so it round-trips cleanly through the YAML run-case
 * descriptor; {@code target} names the header/property for those kinds and is null
 * otherwise.
 */
public record Assertion(Kind kind, String target, String expected) {

    public enum Kind {
        /** Run status equals expected (OK / EXCEPTION / TIMEOUT), case-insensitive. */
        STATUS,
        /** Output body (inline) equals expected exactly. */
        BODY_EQUALS,
        /** Output body (inline) contains expected as a substring. */
        BODY_CONTAINS,
        /** Classified body type equals expected (XML / JSON / TEXT / BINARY), case-insensitive. */
        BODY_TYPE,
        /** Output header {@code target} equals expected. */
        HEADER,
        /** Output property {@code target} equals expected. */
        PROPERTY
    }

    public static Assertion of(Kind kind, String expected) {
        return new Assertion(kind, null, expected);
    }

    public static Assertion of(Kind kind, String target, String expected) {
        return new Assertion(kind, target, expected);
    }
}
