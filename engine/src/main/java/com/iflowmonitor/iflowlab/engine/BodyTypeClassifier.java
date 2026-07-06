package com.iflowmonitor.iflowlab.engine;

import com.iflowmonitor.iflowlab.engine.RunResult.BodyType;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Classifies a result body for rendering (R12): an authoritative Content-Type
 * wins; otherwise the bytes are sniffed. Generic content types
 * (octet-stream, zip, …) are not authoritative and fall through to the sniff.
 * Pure and side-effect free — the app layer applies any user override on top.
 */
public final class BodyTypeClassifier {

    private BodyTypeClassifier() {}

    public static BodyType classify(byte[] body, String contentType) {
        BodyType declared = fromContentType(contentType);
        if (declared != null) {
            return declared;
        }
        return sniff(body == null ? new byte[0] : body);
    }

    private static BodyType fromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String ct = contentType.toLowerCase();
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi);
        }
        ct = ct.trim();
        if (ct.endsWith("+xml") || ct.equals("application/xml") || ct.equals("text/xml")) {
            return BodyType.XML;
        }
        if (ct.endsWith("+json") || ct.equals("application/json")) {
            return BodyType.JSON;
        }
        if (ct.startsWith("text/")) {
            return BodyType.TEXT;
        }
        return null; // generic/binary → sniff
    }

    private static BodyType sniff(byte[] body) {
        if (body.length == 0) {
            return BodyType.TEXT;
        }
        int i = 0;
        while (i < body.length && isAsciiWhitespace(body[i])) {
            i++;
        }
        if (i == body.length) {
            return BodyType.TEXT; // all whitespace
        }
        char first = (char) (body[i] & 0xFF);
        if (first == '<') {
            return BodyType.XML;
        }
        if (first == '{' || first == '[') {
            return BodyType.JSON;
        }
        return isProbablyText(body) ? BodyType.TEXT : BodyType.BINARY;
    }

    private static boolean isAsciiWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }

    private static boolean isProbablyText(byte[] body) {
        for (byte b : body) {
            int u = b & 0xFF;
            if (u == 0) {
                return false; // NUL → binary
            }
            if (u < 0x20 && b != '\t' && b != '\n' && b != '\r') {
                return false; // other C0 control → binary
            }
        }
        // Must also be valid UTF-8.
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
