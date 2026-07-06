package com.iflowmonitor.iflowlab.app;

import java.util.Map;

/** A {@code messages/<name>/} fixture: body + metadata (R5). */
public record MessageFixture(
        String name, String body, String contentType, Map<String, Object> headers, Map<String, Object> properties) {}
