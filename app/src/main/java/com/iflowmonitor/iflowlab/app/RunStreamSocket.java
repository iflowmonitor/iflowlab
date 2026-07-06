package com.iflowmonitor.iflowlab.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflowmonitor.iflowlab.engine.GroovyRunEngine;
import com.iflowmonitor.iflowlab.engine.RunRequest;
import com.iflowmonitor.iflowlab.engine.RunResult;
import com.iflowmonitor.iflowlab.engine.xslt.XsltEngine;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Streams a run's log lines live over a WebSocket (slice 10). The client sends
 * one {@link RunRequestDto} JSON message; the server runs the script and pushes a
 * {@code {"type":"log","line":…}} frame per log line as it is produced, then a
 * final {@code {"type":"result","result":…}} frame carrying the full envelope.
 *
 * <p>The run blocks a background thread, and log frames are emitted from the
 * engine's own worker thread — neither is the WebSocket callback thread, so (as
 * with {@code DebugSocket}) we send by looking the live connection up in the
 * application-scoped {@link OpenConnections} registry rather than through the
 * {@code @SessionScoped} connection proxy, using the non-blocking {@code sendText}.
 */
@WebSocket(path = "/run/stream")
public class RunStreamSocket {

    private static final ExecutorService ASYNC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "iflowlab-runstream");
        t.setDaemon(true);
        return t;
    });

    private final GroovyRunEngine groovy = new GroovyRunEngine();
    private final XsltEngine xslt = new XsltEngine();
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    WebSocketConnection connection;

    @Inject
    OpenConnections openConnections;

    @Inject
    WorkspaceService workspace;

    @OnTextMessage
    public void onMessage(String raw) {
        final String id = connection.id();
        ASYNC.submit(() -> runAndStream(id, raw));
    }

    private void runAndStream(String connectionId, String raw) {
        RunRequestDto dto;
        try {
            dto = mapper.readValue(raw, RunRequestDto.class);
        } catch (Exception e) {
            send(connectionId, Map.of("type", "error", "message", "bad request: " + e.getMessage()));
            return;
        }
        try {
            RunRequest request = dto.toRunRequest(workspace.readServices());
            RunResult result;
            if ("xslt".equalsIgnoreCase(dto.kind())) {
                result = xslt.run(request); // XSLT has no live log stream — send the result only.
            } else {
                result = groovy.run(request, line -> send(connectionId, Map.of("type", "log", "line", line)));
            }
            send(connectionId, Map.of("type", "result", "result", result));
        } catch (Exception e) {
            send(connectionId, Map.of("type", "error", "message", String.valueOf(e.getMessage())));
        }
    }

    private void send(String connectionId, Object frame) {
        String json;
        try {
            json = mapper.writeValueAsString(frame);
        } catch (Exception e) {
            return;
        }
        openConnections.findByConnectionId(connectionId).ifPresent(
                conn -> conn.sendText(json).subscribe().with(ignored -> {}, failure -> {}));
    }
}
