package com.iflowmonitor.iflowlab.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.engine.RunResult.BodyType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Content-Type → sniff classification (R12). Pure, order matters: header wins over sniff. */
class BodyTypeClassifierTest {

    @Test
    void contentTypeWins_overContent() {
        // Declared JSON even though the bytes look like XML.
        assertThat(BodyTypeClassifier.classify(bytes("<a/>"), "application/json")).isEqualTo(BodyType.JSON);
        assertThat(BodyTypeClassifier.classify(bytes("{}"), "application/xml")).isEqualTo(BodyType.XML);
        assertThat(BodyTypeClassifier.classify(bytes("x"), "text/csv")).isEqualTo(BodyType.TEXT);
    }

    @Test
    void suffixContentTypes_areRecognised() {
        assertThat(BodyTypeClassifier.classify(bytes("<a/>"), "application/soap+xml")).isEqualTo(BodyType.XML);
        assertThat(BodyTypeClassifier.classify(bytes("{}"), "application/vnd.api+json")).isEqualTo(BodyType.JSON);
    }

    @Test
    void sniffsWhenContentTypeAbsentOrGeneric() {
        assertThat(BodyTypeClassifier.classify(bytes("  <root>x</root>"), null)).isEqualTo(BodyType.XML);
        assertThat(BodyTypeClassifier.classify(bytes("  {\"a\":1}"), null)).isEqualTo(BodyType.JSON);
        assertThat(BodyTypeClassifier.classify(bytes("[1,2]"), "application/octet-stream")).isEqualTo(BodyType.JSON);
        assertThat(BodyTypeClassifier.classify(bytes("plain words"), null)).isEqualTo(BodyType.TEXT);
    }

    @Test
    void nonTextBytes_areBinary() {
        byte[] zipMagic = {0x50, 0x4b, 0x03, 0x04, 0x00, (byte) 0xff, (byte) 0xfe};
        assertThat(BodyTypeClassifier.classify(zipMagic, null)).isEqualTo(BodyType.BINARY);
        assertThat(BodyTypeClassifier.classify(zipMagic, "application/zip")).isEqualTo(BodyType.BINARY);
    }

    @Test
    void emptyBody_isText() {
        assertThat(BodyTypeClassifier.classify(new byte[0], null)).isEqualTo(BodyType.TEXT);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
