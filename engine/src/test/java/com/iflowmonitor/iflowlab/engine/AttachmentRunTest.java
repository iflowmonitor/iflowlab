package com.iflowmonitor.iflowlab.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Input attachments seed the message; output attachments surface in the result (slice 8). */
class AttachmentRunTest {

    private final GroovyRunEngine engine = new GroovyRunEngine();

    private static RunRequest request(String script, List<AttachmentInput> in) {
        return new RunRequest(script, "in".getBytes(StandardCharsets.UTF_8), "text/plain",
                Map.of(), Map.of(), List.of(), 5_000L, null, in);
    }

    @Test
    void scriptReadsInputAttachment_andItSurfacesInTheResult() {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) {\n"
                        + "    def a = message.getAttachments().get('invoice.xml')\n"
                        + "    message.setBody(a.getContentType() + ':' + new String(a.getInputStream().readAllBytes()))\n"
                        + "    return message\n"
                        + "}\n";
        RunResult result = engine.run(request(script,
                List.of(new AttachmentInput("invoice.xml", "<inv/>".getBytes(StandardCharsets.UTF_8), "application/xml"))));

        assertThat(result.status()).isEqualTo(RunResult.Status.OK);
        assertThat(result.body().inline()).isEqualTo("application/xml:<inv/>");
        // The input attachment is still present on the outgoing message.
        assertThat(result.attachments()).singleElement().satisfies(v -> {
            assertThat(v.name()).isEqualTo("invoice.xml");
            assertThat(v.contentType()).isEqualTo("application/xml");
            assertThat(v.inline()).isEqualTo("<inv/>");
        });
    }

    @Test
    void scriptAddsOutputAttachment_isSurfaced() {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "import javax.activation.DataHandler\n"
                        + "Message processData(Message message) {\n"
                        + "    message.getAttachments().put('receipt.txt', new DataHandler('OK-123', 'text/plain'))\n"
                        + "    return message\n"
                        + "}\n";
        RunResult result = engine.run(request(script, List.of()));

        assertThat(result.status()).isEqualTo(RunResult.Status.OK);
        assertThat(result.attachments()).singleElement().satisfies(v -> {
            assertThat(v.name()).isEqualTo("receipt.txt");
            assertThat(v.contentType()).isEqualTo("text/plain");
            assertThat(v.inline()).isEqualTo("OK-123");
            assertThat(v.size()).isEqualTo(6);
        });
    }

    @Test
    void noAttachments_isEmptyList() {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) { return message }\n";
        assertThat(engine.run(request(script, List.of())).attachments()).isEmpty();
    }
}
