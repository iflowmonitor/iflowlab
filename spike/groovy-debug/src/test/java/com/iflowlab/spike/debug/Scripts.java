package com.iflowlab.spike.debug;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Test helper: load a Groovy script from test resources. */
final class Scripts {
    private Scripts() {
    }

    static String load(String resource) {
        try (InputStream in = Scripts.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("resource not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sample() {
        return load("sample.groovy");
    }
}
