package com.iflowmonitor.iflowlab.app.debug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflowmonitor.iflowlab.cpimock.LogEntry;
import com.iflowmonitor.iflowlab.cpimock.services.CpiServices;
import com.iflowmonitor.iflowlab.engine.RunRequest;
import com.iflowmonitor.iflowlab.engine.debug.DebugController;
import com.iflowmonitor.iflowlab.engine.debug.StackFrameInfo;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The DAP state machine for one debug connection (O6: single session). Translates
 * a documented DAP subset to {@link DebugController} calls and back. Transport is
 * injected as a {@code Consumer<String>} sender so this stays testable and the
 * engine remains swappable (D8 rationale).
 */
public final class DapDebugSession {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DebugController controller = new DebugController();
    private final Consumer<String> send;
    private final Executor async;
    private final java.util.function.Supplier<CpiServices> services;
    private final AtomicInteger seq = new AtomicInteger();
    private final Set<Integer> breakpoints = new HashSet<>();
    private volatile boolean terminatedSent;

    /** Debug a run with no CPI platform services (used by tests). */
    public DapDebugSession(Consumer<String> send, Executor async) {
        this(send, async, () -> CpiServices.EMPTY);
    }

    public DapDebugSession(Consumer<String> send, Executor async, java.util.function.Supplier<CpiServices> services) {
        this.send = send;
        this.async = async;
        this.services = services;
    }

    /** Cancel the run when the connection closes (single-session cleanup). */
    public void dispose() {
        controller.terminate();
    }

    /**
     * Services for a launch: an inline {@code services} object in the DAP launch
     * arguments (stateless runner, SaaS R1) wins over the workspace supplier.
     */
    private CpiServices launchServices(JsonNode args) {
        JsonNode inline = args.path("services");
        if (inline.isObject()) {
            try {
                return mapper.treeToValue(inline, com.iflowmonitor.iflowlab.app.ServicesDto.class).toCpiServices();
            } catch (Exception e) {
                // Malformed inline services: fall through to the workspace supplier.
            }
        }
        return services.get();
    }

    /**
     * The launch input body as bytes: a base64 {@code bodyBase64} (a binary body
     * uploaded in the workbench) wins over the UTF-8 text {@code body}.
     */
    private static byte[] launchBody(JsonNode args) {
        // hasNonNull, not isMissingNode: the SPA sends bodyBase64:null explicitly, and a
        // JSON null is a present NullNode whose asText() is the literal "null" — which
        // would decode to garbage. hasNonNull treats both absent and null as "no binary".
        if (args.hasNonNull("bodyBase64")) {
            String b64 = args.path("bodyBase64").asText();
            if (!b64.isBlank()) {
                return java.util.Base64.getDecoder().decode(b64.trim());
            }
        }
        return args.path("body").asText("").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public void onRequest(String raw) {
        JsonNode req;
        try {
            req = mapper.readTree(raw);
        } catch (Exception e) {
            return;
        }
        int reqSeq = req.path("seq").asInt();
        String command = req.path("command").asText();
        JsonNode args = req.path("arguments");

        switch (command) {
            case "initialize" -> {
                ObjectNode caps = mapper.createObjectNode();
                caps.put("supportsConfigurationDoneRequest", true);
                caps.put("supportsTerminateRequest", true);
                respond(reqSeq, command, caps);
                event("initialized", null);
            }
            case "setBreakpoints" -> {
                breakpoints.clear();
                ArrayNode verified = mapper.createArrayNode();
                JsonNode bps = args.path("breakpoints");
                if (bps.isArray()) {
                    for (JsonNode bp : bps) {
                        int line = bp.path("line").asInt();
                        breakpoints.add(line);
                        verified.add(mapper.createObjectNode().put("verified", true).put("line", line));
                    }
                }
                controller.setBreakpoints(breakpoints);
                ObjectNode body = mapper.createObjectNode();
                body.set("breakpoints", verified);
                respond(reqSeq, command, body);
            }
            case "configurationDone" -> respond(reqSeq, command, null);
            case "launch" -> {
                Map<String, Object> headers = toMap(args.path("headers"));
                Map<String, Object> properties = toMap(args.path("properties"));
                String contentType = args.hasNonNull("contentType") ? args.path("contentType").asText() : null;
                String function = args.hasNonNull("function") ? args.path("function").asText() : null;
                byte[] launchBody;
                try {
                    launchBody = launchBody(args);
                } catch (IllegalArgumentException e) {
                    // e.g. a malformed base64 body — fail the launch cleanly (the DAP
                    // switch has no outer catch), don't leave the client hanging.
                    outputEvent("stderr", "invalid launch request: " + e.getMessage() + "\n");
                    respond(reqSeq, command, null);
                    event("terminated", null);
                    launchBody = null;
                }
                if (launchBody != null) {
                    RunRequest request = new RunRequest(
                            args.path("script").asText(""),
                            launchBody,
                            contentType, headers, properties, List.of(), 0L, launchServices(args),
                            List.of(), function);
                    controller.setBreakpoints(breakpoints);
                    controller.launch(request);
                    respond(reqSeq, command, null);
                    driveUntilStop("breakpoint");
                }
            }
            case "threads" -> {
                ObjectNode body = mapper.createObjectNode();
                ArrayNode threads = body.putArray("threads");
                threads.add(mapper.createObjectNode().put("id", 1).put("name", "script"));
                respond(reqSeq, command, body);
            }
            case "stackTrace" -> respond(reqSeq, command, stackTraceBody());
            case "scopes" -> respond(reqSeq, command, scopesBody(args));
            case "variables" -> respond(reqSeq, command, variablesBody(args));
            case "continue" -> {
                controller.resume();
                respond(reqSeq, command, mapper.createObjectNode().put("allThreadsContinued", true));
                driveUntilStop("breakpoint");
            }
            case "next" -> {
                controller.stepOver();
                respond(reqSeq, command, null);
                driveUntilStop("step");
            }
            case "stepIn" -> {
                controller.stepInto();
                respond(reqSeq, command, null);
                driveUntilStop("step");
            }
            case "stepOut" -> {
                controller.stepOut();
                respond(reqSeq, command, null);
                driveUntilStop("step");
            }
            case "terminate", "disconnect" -> {
                controller.terminate();
                respond(reqSeq, command, null);
                sendTerminated();
            }
            default -> respond(reqSeq, command, null);
        }
    }

    private void driveUntilStop(String reason) {
        async.execute(() -> {
            boolean paused = controller.awaitStop(60_000);
            if (paused) {
                ObjectNode body = mapper.createObjectNode();
                body.put("reason", reason);
                body.put("threadId", 1);
                body.put("allThreadsStopped", true);
                event("stopped", body);
            } else {
                sendTerminated();
            }
        });
    }

    private ObjectNode stackTraceBody() {
        List<StackFrameInfo> frames = controller.stack();
        ObjectNode body = mapper.createObjectNode();
        ArrayNode arr = body.putArray("stackFrames");
        for (int i = 0; i < frames.size(); i++) {
            StackFrameInfo f = frames.get(i);
            ObjectNode fr = mapper.createObjectNode();
            fr.put("id", i);
            fr.put("name", f.name());
            fr.put("line", f.line());
            fr.put("column", 1);
            arr.add(fr);
        }
        body.put("totalFrames", frames.size());
        return body;
    }

    private ObjectNode scopesBody(JsonNode args) {
        int frameId = args.path("frameId").asInt();
        ObjectNode body = mapper.createObjectNode();
        ArrayNode scopes = body.putArray("scopes");
        ObjectNode locals = mapper.createObjectNode();
        locals.put("name", "Locals");
        locals.put("variablesReference", frameId + 1); // nonzero; decode as frameId in variables
        locals.put("expensive", false);
        scopes.add(locals);
        return body;
    }

    private ObjectNode variablesBody(JsonNode args) {
        int ref = args.path("variablesReference").asInt();
        int frameId = ref - 1;
        List<StackFrameInfo> frames = controller.stack();
        ObjectNode body = mapper.createObjectNode();
        ArrayNode vars = body.putArray("variables");
        if (frameId >= 0 && frameId < frames.size()) {
            for (Map.Entry<String, Object> e : frames.get(frameId).locals().entrySet()) {
                ObjectNode v = mapper.createObjectNode();
                v.put("name", e.getKey());
                v.put("value", String.valueOf(e.getValue()));
                v.put("variablesReference", 0);
                vars.add(v);
            }
        }
        return body;
    }

    private void sendTerminated() {
        if (terminatedSent) {
            return;
        }
        terminatedSent = true;
        String out = controller.capturedOutput();
        if (!out.isEmpty()) {
            outputEvent("stdout", out);
        }
        for (LogEntry entry : controller.logFactory().entries()) {
            outputEvent("console", "[messageLog] " + entry.value() + "\n");
        }
        Throwable cause = controller.exitCause();
        if (cause != null) {
            outputEvent("stderr", cause.getClass().getName() + ": " + cause.getMessage() + "\n");
        }
        event("terminated", null);
    }

    private void outputEvent(String category, String text) {
        ObjectNode body = mapper.createObjectNode();
        body.put("category", category);
        body.put("output", text);
        event("output", body);
    }

    private static Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        }
        return map;
    }

    private void respond(int reqSeq, String command, JsonNode body) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("seq", seq.incrementAndGet());
        msg.put("type", "response");
        msg.put("request_seq", reqSeq);
        msg.put("success", true);
        msg.put("command", command);
        if (body != null) {
            msg.set("body", body);
        }
        emit(msg);
    }

    private void event(String eventName, JsonNode body) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("seq", seq.incrementAndGet());
        msg.put("type", "event");
        msg.put("event", eventName);
        if (body != null) {
            msg.set("body", body);
        }
        emit(msg);
    }

    private void emit(ObjectNode msg) {
        try {
            send.accept(mapper.writeValueAsString(msg));
        } catch (Exception ignored) {
            // connection closed
        }
    }
}
