package com.iflowmonitor.iflowlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.engine.RunRequest;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The {@code /run} DTO boundary: a binary body/attachment arrives base64 and must
 * survive as exact bytes (never forced through UTF-8, which would corrupt a zip or
 * PDF), and the entry function name threads through to the engine request.
 */
class RunRequestDtoTest {

    // Bytes that are NOT valid UTF-8 (0xFF, 0x9c) — proves we decode base64, not getBytes(UTF_8).
    private static final byte[] BINARY = {0x50, 0x4b, 0x03, 0x04, (byte) 0xFF, 0x00, (byte) 0x9c};

    @Test
    void binaryBodyBase64_decodesToExactBytes() {
        String b64 = Base64.getEncoder().encodeToString(BINARY);
        RunRequestDto dto = new RunRequestDto(
                "script", null, b64, "application/zip", null, null, null, "groovy", null, null, null);

        RunRequest req = dto.toRunRequest();

        assertThat(req.body()).isEqualTo(BINARY);
        assertThat(req.contentType()).isEqualTo("application/zip");
    }

    @Test
    void bodyBase64_winsOverText_whenBothPresent() {
        String b64 = Base64.getEncoder().encodeToString(BINARY);
        RunRequestDto dto = new RunRequestDto(
                "script", "ignored-text", b64, "application/zip", null, null, null, "groovy", null, null, null);

        assertThat(dto.toRunRequest().body()).isEqualTo(BINARY);
    }

    @Test
    void attachmentBodyBase64_decodesToExactBytes() {
        byte[] pdf = {0x25, 0x50, 0x44, 0x46, (byte) 0xFF};
        String b64 = Base64.getEncoder().encodeToString(pdf);
        var att = new RunRequestDto.AttachmentDto("doc.pdf", null, b64, "application/pdf");
        RunRequestDto dto = new RunRequestDto(
                "script", null, null, null, null, null, null, "groovy", List.of(att), null, null);

        RunRequest req = dto.toRunRequest();

        assertThat(req.attachments()).singleElement().satisfies(a -> {
            assertThat(a.name()).isEqualTo("doc.pdf");
            assertThat(a.content()).isEqualTo(pdf);
            assertThat(a.contentType()).isEqualTo("application/pdf");
        });
    }

    @Test
    void functionName_threadsThroughToTheRequest() {
        RunRequestDto dto = new RunRequestDto(
                "script", "x", null, "text/plain", null, null, null, "groovy", null, "myEntry", null);

        assertThat(dto.toRunRequest().function()).isEqualTo("myEntry");
    }

    @Test
    void textBody_stillWorks_andDefaultsToProcessData() {
        RunRequestDto dto = new RunRequestDto(
                "script", "hello", null, "text/plain", null, null, null, "groovy", null, null, null);

        RunRequest req = dto.toRunRequest();

        assertThat(new String(req.body())).isEqualTo("hello");
        assertThat(req.function()).isEqualTo("processData");
    }
}
