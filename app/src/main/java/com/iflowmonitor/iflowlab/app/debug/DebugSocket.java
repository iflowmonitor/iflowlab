package com.iflowmonitor.iflowlab.app.debug;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.OpenConnections;
import com.iflowmonitor.iflowlab.app.WorkspaceService;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DAP-over-WebSocket endpoint for the debugger (D8). One {@link DapDebugSession}
 * per connection — a single active debug session (O6). The engine stays swappable
 * behind the DAP contract.
 *
 * <p>Async stop/terminate events are emitted from a background thread by
 * {@code driveUntilStop} (which blocks on the worker parking at a breakpoint). The
 * injected {@link WebSocketConnection} is a {@code @SessionScoped} proxy that only
 * resolves on the connection's own callback thread, so sending from the background
 * thread throws {@code ContextNotActiveException}. We instead look the live
 * connection up by id via the application-scoped {@link OpenConnections} registry,
 * which is safe from any thread, and send with the non-blocking {@code sendText}.
 */
@WebSocket(path = "/debug")
public class DebugSocket {

    private static final Map<String, DapDebugSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ExecutorService ASYNC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "iflowlab-dap");
        t.setDaemon(true);
        return t;
    });

    @Inject
    WebSocketConnection connection;

    @Inject
    OpenConnections openConnections;

    @Inject
    WorkspaceService workspace;

    @OnOpen
    public void onOpen() {
        String id = connection.id();
        SESSIONS.put(id, new DapDebugSession(json -> send(id, json), ASYNC, workspace::readServices));
    }

    private void send(String connectionId, String json) {
        openConnections.findByConnectionId(connectionId).ifPresent(
                conn -> conn.sendText(json).subscribe().with(ignored -> {}, failure -> {}));
    }

    @OnTextMessage
    public void onMessage(String raw) {
        DapDebugSession session = SESSIONS.get(connection.id());
        if (session != null) {
            session.onRequest(raw);
        }
    }

    @OnClose
    public void onClose() {
        DapDebugSession session = SESSIONS.remove(connection.id());
        if (session != null) {
            session.dispose();
        }
    }
}
