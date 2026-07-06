package com.iflowmonitor.iflowlab.app;

import java.util.List;
import java.util.Map;

/** A {@code messages/<name>/} fixture: body + metadata + attachments (R5, slice 8). */
public record MessageFixture(
        String name,
        String body,
        String contentType,
        Map<String, Object> headers,
        Map<String, Object> properties,
        List<Attachment> attachments) {

    /** One fixture-backed attachment, stored as a file under {@code messages/<name>/attachments/}. */
    public record Attachment(String name, String body, String contentType) {}
}
