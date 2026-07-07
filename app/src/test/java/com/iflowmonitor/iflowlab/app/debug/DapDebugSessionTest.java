package com.iflowmonitor.iflowlab.app.debug;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Exercises the DAP state machine end-to-end without WebSocket/Quarkus, using a
 * capturing sender and an inline executor (so driveUntilStop runs synchronously).
 */
@Timeout(10)
class DapDebugSessionTest {

    private static final String SCRIPT =
            "import com.sap.gateway.ip.core.customdev.util.Message\n"
                    + "Message processData(Message message) {\n"
                    + "    def a = 1\n"
                    + "    def b = a + 1\n"
                    + "    message.setBody(b.toString())\n"
                    + "    return message\n"
                    + "}\n";

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> sent = new ArrayList<>();
    private final DapDebugSession session =
            new DapDebugSession(this::capture, Runnable::run); // inline executor

    private void capture(String json) {
        try {
            sent.add(mapper.readTree(json));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void request(String command, String argsJson) {
        session.onRequest("{\"seq\":1,\"type\":\"request\",\"command\":\"" + command + "\",\"arguments\":" + argsJson + "}");
    }

    private List<JsonNode> events(String name) {
        return sent.stream().filter(n -> "event".equals(n.path("type").asText())
                && name.equals(n.path("event").asText())).toList();
    }

    private JsonNode lastResponse(String command) {
        return sent.stream().filter(n -> "response".equals(n.path("type").asText())
                && command.equals(n.path("command").asText())).reduce((a, b) -> b).orElseThrow();
    }

    @Test
    void fullFlow_breakpoint_inspect_thenContinueToTermination() {
        request("initialize", "{}");
        assertThat(lastResponse("initialize").path("body").path("supportsConfigurationDoneRequest").asBoolean()).isTrue();
        assertThat(events("initialized")).hasSize(1);

        request("setBreakpoints", "{\"breakpoints\":[{\"line\":4}]}");
        assertThat(lastResponse("setBreakpoints").path("body").path("breakpoints").get(0).path("verified").asBoolean())
                .isTrue();

        // launch runs to the breakpoint on line 4 and emits a stopped event.
        String launchArgs = "{\"script\":" + mapper.valueToTree(SCRIPT) + ",\"body\":\"in\"}";
        request("launch", launchArgs);
        assertThat(events("stopped")).hasSize(1);
        assertThat(events("stopped").get(0).path("body").path("reason").asText()).isEqualTo("breakpoint");

        // stackTrace → processData at line 4.
        request("stackTrace", "{\"threadId\":1}");
        JsonNode frame0 = lastResponse("stackTrace").path("body").path("stackFrames").get(0);
        assertThat(frame0.path("name").asText()).isEqualTo("processData");
        assertThat(frame0.path("line").asInt()).isEqualTo(4);

        // scopes → variables shows local a=1 (b not yet declared at line 4).
        request("scopes", "{\"frameId\":0}");
        int varRef = lastResponse("scopes").path("body").path("scopes").get(0).path("variablesReference").asInt();
        request("variables", "{\"variablesReference\":" + varRef + "}");
        JsonNode vars = lastResponse("variables").path("body").path("variables");
        assertThat(vars).anySatisfy(v -> {
            assertThat(v.path("name").asText()).isEqualTo("a");
            assertThat(v.path("value").asText()).isEqualTo("1");
        });

        // continue → no more breakpoints → terminated.
        request("continue", "{\"threadId\":1}");
        assertThat(events("terminated")).hasSize(1);
    }

    @Test
    void terminated_emitsFinalResultBody_soContinueToEndShowsOutput() {
        // A script that only setBody()s (no println) produced no debug output before,
        // so continuing to the end left the panel blank. The final message body is now
        // surfaced as an output event on clean termination.
        request("initialize", "{}");
        request("configurationDone", "{}");
        String args = "{\"script\":" + mapper.valueToTree(SCRIPT) + ",\"body\":\"in\"}";
        request("launch", args);

        assertThat(events("terminated")).isNotEmpty();
        String out = events("output").stream()
                .map(e -> e.path("body").path("output").asText())
                .reduce("", String::concat);
        // SCRIPT ends with message.setBody(b.toString()) where b == 2.
        assertThat(out).contains("2");
    }

    @Test
    void launch_withExplicitNulls_usesTextBodyAndDefaultProcessData() {
        // Regression: the SPA sends bodyBase64:null and function:null explicitly. A JSON
        // null must mean "absent" — text body + default processData — not the literal
        // string "null" (which would decode to garbage bytes and call invokeMethod("null")).
        request("initialize", "{}");
        request("configurationDone", "{}");
        String script = "import com.sap.gateway.ip.core.customdev.util.Message\n"
                + "Message processData(Message message) {\n"
                + "    if (message.getBody(String) != 'hello') {\n"
                + "        throw new RuntimeException('corrupt body: ' + message.getBody(String))\n"
                + "    }\n"
                + "    return message\n"
                + "}\n";
        String args = "{\"script\":" + mapper.valueToTree(script)
                + ",\"body\":\"hello\",\"bodyBase64\":null,\"function\":null,\"contentType\":null}";
        request("launch", args);

        assertThat(events("terminated")).isNotEmpty();
        String stderr = events("output").stream()
                .filter(e -> "stderr".equals(e.path("body").path("category").asText()))
                .map(e -> e.path("body").path("output").asText())
                .reduce("", String::concat);
        assertThat(stderr).doesNotContain("MissingMethod").doesNotContain("corrupt body");
    }

    @Test
    void dataBreakpoint_stopsOnValueChange_withReasonAndDescription() {
        String mutating =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) {\n"
                        + "    def x = 1\n"
                        + "    x = 2\n"
                        + "    x = 3\n"
                        + "    message.setBody(x.toString())\n"
                        + "    return message\n"
                        + "}\n";
        request("initialize", "{}");
        assertThat(lastResponse("initialize").path("body").path("supportsDataBreakpoints").asBoolean()).isTrue();

        // The SPA first asks whether `x` can be watched, then sets it by dataId.
        request("dataBreakpointInfo", "{\"name\":\"x\"}");
        String dataId = lastResponse("dataBreakpointInfo").path("body").path("dataId").asText();
        assertThat(dataId).isEqualTo("x");
        request("setDataBreakpoints", "{\"breakpoints\":[{\"dataId\":\"x\"}]}");
        request("configurationDone", "{}");

        String launchArgs = "{\"script\":" + mapper.valueToTree(mutating) + ",\"body\":\"in\"}";
        request("launch", launchArgs);

        assertThat(events("stopped")).hasSize(1);
        JsonNode stop = events("stopped").get(0).path("body");
        assertThat(stop.path("reason").asText()).isEqualTo("data breakpoint");
        assertThat(stop.path("description").asText()).contains("x");

        // The variable is at its changed value (x=2) where we paused.
        request("stackTrace", "{\"threadId\":1}");
        request("scopes", "{\"frameId\":0}");
        int varRef = lastResponse("scopes").path("body").path("scopes").get(0).path("variablesReference").asInt();
        request("variables", "{\"variablesReference\":" + varRef + "}");
        assertThat(lastResponse("variables").path("body").path("variables")).anySatisfy(v -> {
            assertThat(v.path("name").asText()).isEqualTo("x");
            assertThat(v.path("value").asText()).isEqualTo("2");
        });

        request("continue", "{\"threadId\":1}");
        // x becomes 3 → stops again, then no more changes → terminates on the next continue.
        assertThat(events("stopped")).hasSize(2);
        request("continue", "{\"threadId\":1}");
        assertThat(events("terminated")).hasSize(1);
    }

    @Test
    void conditionalDataBreakpoint_stopsOnFalseToTrueEdge_withConditionInDescription() {
        String mutating =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) {\n"
                        + "    def x = 1\n"
                        + "    x = 2\n"
                        + "    x = 3\n"
                        + "    message.setBody(x.toString())\n"
                        + "    return message\n"
                        + "}\n";
        request("initialize", "{}");
        request("dataBreakpointInfo", "{\"name\":\"x\"}");
        // A DAP `condition` turns the watch conditional: stop when x first equals 3.
        request("setDataBreakpoints", "{\"breakpoints\":[{\"dataId\":\"x\",\"condition\":\"== 3\"}]}");
        request("configurationDone", "{}");

        String launchArgs = "{\"script\":" + mapper.valueToTree(mutating) + ",\"body\":\"in\"}";
        request("launch", launchArgs);

        assertThat(events("stopped")).hasSize(1);
        JsonNode stop = events("stopped").get(0).path("body");
        assertThat(stop.path("reason").asText()).isEqualTo("data breakpoint");
        assertThat(stop.path("description").asText()).isEqualTo("x == 3");

        // Paused where the predicate first held: x == 3.
        request("stackTrace", "{\"threadId\":1}");
        request("scopes", "{\"frameId\":0}");
        int varRef = lastResponse("scopes").path("body").path("scopes").get(0).path("variablesReference").asInt();
        request("variables", "{\"variablesReference\":" + varRef + "}");
        assertThat(lastResponse("variables").path("body").path("variables")).anySatisfy(v -> {
            assertThat(v.path("name").asText()).isEqualTo("x");
            assertThat(v.path("value").asText()).isEqualTo("3");
        });

        // No further false→true edge → terminates on continue.
        request("continue", "{\"threadId\":1}");
        assertThat(events("terminated")).hasSize(1);
    }

    @Test
    void launch_withInlineServices_bindsThemForTheDebuggedRun() {
        // No workspace supplier configured — services arrive in the DAP launch args (SaaS R1).
        String credScript =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "import com.sap.it.api.ITApiFactory\n"
                        + "import com.sap.it.api.securestore.SecureStoreService\n"
                        + "Message processData(Message message) {\n"
                        + "    def store = ITApiFactory.getService(SecureStoreService.class, null)\n"
                        + "    def user = store.getUserCredential('Api').getUsername()\n"
                        + "    message.setBody(user)\n"
                        + "    return message\n"
                        + "}\n";
        request("initialize", "{}");
        request("setBreakpoints", "{\"breakpoints\":[{\"line\":7}]}");
        String launchArgs = "{\"script\":" + mapper.valueToTree(credScript) + ",\"body\":\"in\","
                + "\"services\":{\"credentials\":{\"Api\":{\"username\":\"dap-user\",\"password\":\"pw\"}}}}";
        request("launch", launchArgs);
        assertThat(events("stopped")).hasSize(1);

        // At line 7 the local `user` already holds the credential resolved from inline services.
        request("stackTrace", "{\"threadId\":1}");
        request("scopes", "{\"frameId\":0}");
        int varRef = lastResponse("scopes").path("body").path("scopes").get(0).path("variablesReference").asInt();
        request("variables", "{\"variablesReference\":" + varRef + "}");
        JsonNode vars = lastResponse("variables").path("body").path("variables");
        assertThat(vars).anySatisfy(v -> {
            assertThat(v.path("name").asText()).isEqualTo("user");
            assertThat(v.path("value").asText()).isEqualTo("dap-user");
        });

        request("continue", "{\"threadId\":1}");
        assertThat(events("terminated")).hasSize(1);
    }
}
