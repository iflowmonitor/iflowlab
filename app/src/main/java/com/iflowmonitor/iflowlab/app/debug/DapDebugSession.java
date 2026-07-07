package com.iflowmonitor.iflowlab.app.debug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflowmonitor.iflowlab.cpimock.LogEntry;
import com.iflowmonitor.iflowlab.cpimock.services.CpiServices;
import com.iflowmonitor.iflowlab.engine.RunRequest;
import com.iflowmonitor.iflowlab.engine.RunResult;
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
                caps.put("supportsDataBreakpoints", true);
                caps.put("supportsSetVariable", true);
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
            case "dataBreakpointInfo" -> {
                // The client asks whether a variable can be watched; we key the data
                // breakpoint by the variable name itself (returned as dataId).
                String name = args.hasNonNull("name") ? args.path("name").asText() : "";
                ObjectNode body = mapper.createObjectNode();
                if (name.isBlank()) {
                    body.set("dataId", mapper.nullNode());
                    body.put("description", "no variable");
                } else {
                    body.put("dataId", name);
                    body.put("description", name + " (break on value change)");
                    body.putArray("accessTypes").add("write");
                    body.put("canPersist", false);
                }
                respond(reqSeq, command, body);
            }
            case "setDataBreakpoints" -> {
                // Each breakpoint carries its variable name as dataId and an optional
                // DAP `condition`: empty = break on change, "<op> <value>" = break on
                // the condition's false→true edge (conditional watchpoints).
                Map<String, String> specs = new LinkedHashMap<>();
                ArrayNode verified = mapper.createArrayNode();
                JsonNode dbps = args.path("breakpoints");
                if (dbps.isArray()) {
                    for (JsonNode bp : dbps) {
                        String dataId = bp.path("dataId").asText("");
                        if (!dataId.isBlank()) {
                            specs.put(dataId, bp.path("condition").asText(""));
                        }
                        verified.add(mapper.createObjectNode().put("verified", true));
                    }
                }
                controller.setDataBreakpoints(specs);
                ObjectNode body = mapper.createObjectNode();
                body.set("breakpoints", verified);
                respond(reqSeq, command, body);
            }
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
            case "setVariable" -> {
                // Edit a paused value: locals apply at the next statement (override),
                // message headers/properties mutate the live message immediately.
                int kind = args.path("variablesReference").asInt() % 10;
                String name = args.path("name").asText("");
                String value = args.path("value").asText("");
                switch (kind) {
                    case SCOPE_HEADERS -> controller.setMessageHeader(name, value);
                    case SCOPE_PROPERTIES -> controller.setMessageProperty(name, value);
                    case SCOPE_LOCALS -> controller.setLocalOverride(name, value);
                    default -> { /* attachments are read-only */ }
                }
                respond(reqSeq, command, mapper.createObjectNode().put("value", value));
            }
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

    private void driveUntilStop(String fallbackReason) {
        async.execute(() -> {
            boolean paused = controller.awaitStop(60_000);
            if (paused) {
                // A continue/step can actually stop for a data breakpoint, so the reason
                // comes from the engine, not from the request that resumed the run.
                String reason = controller.stopReason();
                if (reason == null || reason.isBlank()) {
                    reason = fallbackReason;
                }
                ObjectNode body = mapper.createObjectNode();
                body.put("reason", reason);
                if ("data breakpoint".equals(reason) && !controller.stopDetail().isBlank()) {
                    // stopDetail is the full human phrase, e.g. "x changed" or "x >= 3".
                    body.put("description", controller.stopDetail());
                }
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

    // A variablesReference encodes frameId and scope kind: ref = frameId * 10 + kind.
    private static final int SCOPE_LOCALS = 1;
    private static final int SCOPE_HEADERS = 2;
    private static final int SCOPE_PROPERTIES = 3;
    private static final int SCOPE_ATTACHMENTS = 4;

    private ObjectNode scopesBody(JsonNode args) {
        int frameId = args.path("frameId").asInt();
        ObjectNode body = mapper.createObjectNode();
        ArrayNode scopes = body.putArray("scopes");
        scopes.add(scope("Locals", frameId * 10 + SCOPE_LOCALS));
        scopes.add(scope("Headers", frameId * 10 + SCOPE_HEADERS));
        scopes.add(scope("Properties", frameId * 10 + SCOPE_PROPERTIES));
        scopes.add(scope("Attachments", frameId * 10 + SCOPE_ATTACHMENTS));
        return body;
    }

    private ObjectNode scope(String name, int ref) {
        ObjectNode s = mapper.createObjectNode();
        s.put("name", name);
        s.put("variablesReference", ref);
        s.put("expensive", false);
        return s;
    }

    private ObjectNode variablesBody(JsonNode args) {
        int ref = args.path("variablesReference").asInt();
        int frameId = ref / 10;
        int kind = ref % 10;
        ObjectNode body = mapper.createObjectNode();
        ArrayNode vars = body.putArray("variables");
        com.sap.gateway.ip.core.customdev.util.Message msg = controller.message();
        switch (kind) {
            case SCOPE_LOCALS -> {
                List<StackFrameInfo> frames = controller.stack();
                if (frameId >= 0 && frameId < frames.size()) {
                    for (Map.Entry<String, Object> e : frames.get(frameId).locals().entrySet()) {
                        vars.add(variable(e.getKey(), String.valueOf(e.getValue())));
                    }
                }
            }
            case SCOPE_HEADERS -> {
                if (msg != null) {
                    msg.getHeaders().forEach((k, v) -> vars.add(variable(k, String.valueOf(v))));
                }
            }
            case SCOPE_PROPERTIES -> {
                if (msg != null) {
                    msg.getProperties().forEach((k, v) -> vars.add(variable(k, String.valueOf(v))));
                }
            }
            case SCOPE_ATTACHMENTS -> {
                if (msg != null && msg.getAttachments() != null) {
                    msg.getAttachments().forEach((k, dh) -> vars.add(variable(k, dh.getContentType())));
                }
            }
            default -> { /* unknown scope */ }
        }
        return body;
    }

    private ObjectNode variable(String name, String value) {
        ObjectNode v = mapper.createObjectNode();
        v.put("name", name);
        v.put("value", value);
        v.put("variablesReference", 0);
        return v;
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
        // Emit the full run envelope (body, header/property diff, attachments, logs) as a
        // custom event so a debug run that finishes shows the SAME unified Output as a plain
        // run — not just body in the debug panel. Null when cancelled or still running.
        RunResult result = controller.buildResult();
        if (result != null) {
            event("iflowlabResult", mapper.valueToTree(result));
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
