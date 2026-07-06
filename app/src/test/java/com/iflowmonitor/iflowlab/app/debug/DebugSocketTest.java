package com.iflowmonitor.iflowlab.app.debug;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Exercises the real DAP-over-WebSocket transport end-to-end against the running
 * Quarkus server. Regression guard for the threading bug where {@code stopped} /
 * {@code terminated} events — emitted from a background thread by driveUntilStop —
 * were silently dropped because {@code sendTextAndAwait} only works on Vert.x
 * executor threads. A capturing-sender unit test cannot catch this; only the real
 * connection can.
 */
@QuarkusTest
@Timeout(30)
class DebugSocketTest {

    private static final String SCRIPT =
            "import com.sap.gateway.ip.core.customdev.util.Message\n"
                    + "Message processData(Message message) {\n"
                    + "    def a = 1\n"
                    + "    def b = a + 1\n"
                    + "    message.setBody(b.toString())\n"
                    + "    return message\n"
                    + "}\n";

    @TestHTTPResource("/debug")
    URI debugUri;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger seq = new AtomicInteger(1);

    @Test
    void realWebSocket_stopsAtBreakpoint_thenContinuesToTermination() throws Exception {
        WebSocket ws = connect();
        try {
            send(ws, "initialize", "{}");
            awaitEvent("initialized");

            send(ws, "setBreakpoints", "{\"breakpoints\":[{\"line\":4}]}");
            send(ws, "configurationDone", "{}");
            send(ws, "launch", "{\"script\":" + mapper.valueToTree(SCRIPT) + ",\"body\":\"in\"}");

            // The event that the blocking-send bug used to swallow.
            JsonNode stopped = awaitEvent("stopped");
            assertThat(stopped.path("body").path("reason").asText()).isEqualTo("breakpoint");

            send(ws, "stackTrace", "{\"threadId\":1}");
            JsonNode frame = awaitResponse("stackTrace").path("body").path("stackFrames").get(0);
            assertThat(frame.path("name").asText()).isEqualTo("processData");
            assertThat(frame.path("line").asInt()).isEqualTo(4);

            send(ws, "continue", "{\"threadId\":1}");
            awaitEvent("terminated");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    private WebSocket connect() throws Exception {
        URI wsUri = URI.create(debugUri.toString().replaceFirst("^http", "ws"));
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(wsUri, new Collector())
                .get(10, TimeUnit.SECONDS);
    }

    private void send(WebSocket ws, String command, String argsJson) {
        String msg = "{\"seq\":" + seq.getAndIncrement() + ",\"type\":\"request\",\"command\":\""
                + command + "\",\"arguments\":" + argsJson + "}";
        ws.sendText(msg, true).toCompletableFuture().join();
    }

    private JsonNode awaitEvent(String name) {
        return await(() -> received.stream()
                .filter(n -> "event".equals(n.path("type").asText()) && name.equals(n.path("event").asText()))
                .reduce((a, b) -> b).orElse(null), "event " + name);
    }

    private JsonNode awaitResponse(String command) {
        return await(() -> received.stream()
                .filter(n -> "response".equals(n.path("type").asText()) && command.equals(n.path("command").asText()))
                .reduce((a, b) -> b).orElse(null), "response " + command);
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
