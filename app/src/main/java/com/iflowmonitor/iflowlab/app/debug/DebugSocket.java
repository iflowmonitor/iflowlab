package com.iflowmonitor.iflowlab.app.debug;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
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

    @OnOpen
    public void onOpen() {
        SESSIONS.put(connection.id(), new DapDebugSession(json -> connection.sendTextAndAwait(json), ASYNC));
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
