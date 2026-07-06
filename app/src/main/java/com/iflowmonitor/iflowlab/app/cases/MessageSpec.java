package com.iflowmonitor.iflowlab.app.cases;

import java.util.Map;

/**
 * The input message a run-case feeds to the script — stored inline so a case is
 * self-contained (no dangling fixture reference to break; D4 filesystem truth).
 */
public record MessageSpec(String body, String contentType, Map<String, Object> headers, Map<String, Object> properties) {}
