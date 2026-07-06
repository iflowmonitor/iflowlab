package com.iflowmonitor.iflowlab.engine;

/** An input attachment seeded onto the message before a run (slice 8). */
public record AttachmentInput(String name, byte[] content, String contentType) {}
