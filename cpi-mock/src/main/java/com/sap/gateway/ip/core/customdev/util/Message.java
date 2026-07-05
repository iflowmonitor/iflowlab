package com.sap.gateway.ip.core.customdev.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.activation.DataHandler;

/**
 * Clean-room mock of the SAP CPI {@code Message} passed to a Groovy
 * {@code Message processData(Message message)}.
 *
 * <p>Clean-room per D5: signatures follow SAP's published javadoc
 * (com.sap.it.script.custom-development API); behaviour is validated against
 * oracles (R11). SAP's own jar is not redistributable and no code is copied.
 *
 * <p>Body coercion mirrors CPI: a body may be stored as a String, byte[], or
 * InputStream, and {@link #getBody(Class)} converts on demand. An InputStream
 * body is <em>one-shot</em> — reading it drains it, exactly as in CPI.
 */
public class Message {

    private Object body;
    private Map<String, Object> headers = new LinkedHashMap<>();
    private Map<String, Object> properties = new LinkedHashMap<>();
    private Map<String, DataHandler> attachments = new LinkedHashMap<>();

    public Object getBody() {
        return body;
    }

    public void setBody(Object exchangeBody) {
        this.body = exchangeBody;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBody(Class<T> type) {
        if (body == null) {
            return null;
        }
        if (type == String.class) {
            return (T) new String(toBytes(body), StandardCharsets.UTF_8);
        }
        if (type == byte[].class) {
            return (T) toBytes(body);
        }
        if (type == InputStream.class) {
            if (body instanceof InputStream in) {
                return (T) in; // same stream — one-shot
            }
            return (T) new ByteArrayInputStream(toBytes(body));
        }
        if (type.isInstance(body)) {
            return (T) body;
        }
        throw new UnsupportedOperationException("getBody(" + type + ") not supported by the mock");
    }

    public long getBodySize() {
        if (body == null) {
            return 0L;
        }
        if (body instanceof byte[] b) {
            return b.length;
        }
        if (body instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8).length;
        }
        return -1L; // unknown for a live stream
    }

    // --- Headers -----------------------------------------------------------

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> exchangeHeaders) {
        this.headers = exchangeHeaders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(exchangeHeaders);
    }

    public void setHeader(String name, Object value) {
        headers.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getHeader(String headerName, Class<T> headerType) {
        return (T) headers.get(headerName);
    }

    // --- Properties --------------------------------------------------------

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> exchangeProperties) {
        this.properties = exchangeProperties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(exchangeProperties);
    }

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    // --- Attachments -------------------------------------------------------
    // Slice 1: API-present, in-memory only. Not fixture-seeded, not rendered (R9).

    public Map<String, DataHandler> getAttachments() {
        return attachments;
    }

    public void setAttachments(Map<String, DataHandler> attachments) {
        this.attachments = attachments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attachments);
    }

    /**
     * Reads {@code source} into bytes. If it is an InputStream it is consumed
     * (drained) — the CPI one-shot semantics.
     */
    private static byte[] toBytes(Object source) {
        if (source instanceof byte[] b) {
            return b;
        }
        if (source instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
        if (source instanceof InputStream in) {
            try {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return String.valueOf(source).getBytes(StandardCharsets.UTF_8);
    }
}
