package com.iflowmonitor.iflowlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The /run/stream WebSocket pushes log frames live, then a final result frame (slice 10). */
@QuarkusTest
@Timeout(30)
class RunStreamSocketTest {

    private static final String SCRIPT =
            "import com.sap.gateway.ip.core.customdev.util.Message\n"
                    + "Message processData(Message message) {\n"
                    + "    def log = messageLogFactory.getMessageLog(message)\n"
                    + "    log.setStringProperty('Step', 'begin')\n"
                    + "    println 'working'\n"
                    + "    log.setStringProperty('Step', 'done')\n"
                    + "    message.setBody('OK')\n"
                    + "    return message\n"
                    + "}\n";

    @TestHTTPResource("/run/stream")
    URI streamUri;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> received = new CopyOnWriteArrayList<>();

    @Test
    void streamsLogFrames_thenResult() throws Exception {
        WebSocket ws = connect();
        try {
            String req = "{\"script\":" + mapper.valueToTree(SCRIPT) + ",\"body\":\"in\",\"kind\":\"groovy\"}";
            ws.sendText(req, true).toCompletableFuture().join();

            JsonNode result = await(() -> received.stream()
                    .filter(n -> "result".equals(n.path("type").asText()))
                    .findFirst().orElse(null), "result frame");

            List<String> logMessages = received.stream()
                    .filter(n -> "log".equals(n.path("type").asText()))
                    .map(n -> n.path("line").path("message").asText())
                    .collect(Collectors.toList());

            assertThat(logMessages).contains("begin", "working", "done");
            assertThat(result.path("result").path("status").asText()).isEqualTo("OK");
            assertThat(result.path("result").path("body").path("inline").asText()).isEqualTo("OK");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    private WebSocket connect() throws Exception {
        URI wsUri = URI.create(streamUri.toString().replaceFirst("^http", "ws"));
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(wsUri, new Collector())
                .get(10, TimeUnit.SECONDS);
    }

    private JsonNode await(Supplier<JsonNode> probe, String what) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            JsonNode hit = probe.get();
            if (hit != null) {
                return hit;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("timed out waiting for " + what + "; received=" + received);
    }

    private final class Collector implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                try {
                    received.add(mapper.readTree(buffer.toString()));
                } catch (Exception ignored) {
                    // non-JSON frame; skip
                }
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
    }
}
