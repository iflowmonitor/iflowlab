package com.iflowmonitor.iflowlab.engine.debug;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.engine.RunRequest;
import com.iflowmonitor.iflowlab.engine.RunResult;
import com.sap.gateway.ip.core.customdev.util.Message;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class DebugControllerTest {

    // 1: import ...
    // 2: Message processData(Message message) {
    // 3:     def a = 1
    // 4:     def b = helper(a)
    // 5:     def c = b + 1
    // 6:     message.setBody(c.toString())
    // 7:     return message
    // 8: }
    // 9: int helper(int x) {
    // 10:     def y = x * 2
    // 11:     return y
    // 12: }
    private static final String SCRIPT =
            "import com.sap.gateway.ip.core.customdev.util.Message\n"
                    + "Message processData(Message message) {\n"
                    + "    def a = 1\n"
                    + "    def b = helper(a)\n"
                    + "    def c = b + 1\n"
                    + "    message.setBody(c.toString())\n"
                    + "    return message\n"
                    + "}\n"
                    + "int helper(int x) {\n"
                    + "    def y = x * 2\n"
                    + "    return y\n"
                    + "}\n";

    private DebugController launchAt(Set<Integer> breakpoints) {
        DebugController c = new DebugController();
        c.setBreakpoints(breakpoints);
        c.launch(RunRequest.ofScriptAndText(SCRIPT, "in"));
        assertThat(c.awaitStop(3000)).isTrue();
        return c;
    }

    @Test
    void pausesAtBreakpoint_exposesLineStackAndLocals() {
        DebugController c = launchAt(Set.of(5));

        assertThat(c.currentLine()).isEqualTo(5);
        assertThat(c.stack()).isNotEmpty();
        assertThat(c.stack().get(0).name()).isEqualTo("processData");
        assertThat(c.stack().get(0).locals()).containsEntry("a", 1).containsEntry("b", 2);
    }

    @Test
    void stepOver_aMethodCall_staysInCaller() {
        // The P4 fix: at the helper() call (line 4), step-over must land on line 5,
        // NOT dive into helper (line 10). Requires the runtime call-depth counter.
        DebugController c = launchAt(Set.of(4));
        c.stepOver();

        assertThat(c.awaitStop(3000)).isTrue();
        assertThat(c.currentLine()).isEqualTo(5);
    }

    @Test
    void stepInto_aMethodCall_entersCallee() {
        DebugController c = launchAt(Set.of(4));
        c.stepInto();

        assertThat(c.awaitStop(3000)).isTrue();
        assertThat(c.currentLine()).isEqualTo(10);
        assertThat(c.stack().get(0).name()).isEqualTo("helper");
    }

    @Test
    void stepOut_ofCallee_returnsToCaller() {
        DebugController c = launchAt(Set.of(10)); // inside helper
        c.stepOut();

        assertThat(c.awaitStop(3000)).isTrue();
        // Back in processData continuing after the call.
        assertThat(c.stack().get(0).name()).isEqualTo("processData");
    }

    @Test
    void resume_runsToCompletion_withTransformedResult() {
        DebugController c = launchAt(Set.of(3));
        c.resume();

        // No more breakpoints → finishes.
        assertThat(c.awaitStop(3000)).isFalse();
        assertThat(c.isFinished()).isTrue();
        assertThat(c.exitCause()).isNull();
    }

    // 1: import ...
    // 2: Message processData(Message message) {
    // 3:     def x = 1
    // 4:     x = 2
    // 5:     x = 3
    // 6:     message.setBody(x.toString())
    // 7:     return message
    // 8: }
    private static final String MUTATING_SCRIPT =
            "import com.sap.gateway.ip.core.customdev.util.Message\n"
                    + "Message processData(Message message) {\n"
                    + "    def x = 1\n"
                    + "    x = 2\n"
                    + "    x = 3\n"
                    + "    message.setBody(x.toString())\n"
                    + "    return message\n"
                    + "}\n";

    @Test
    void dataBreakpoint_stopsWhenAWatchedVariableChanges() {
        DebugController c = new DebugController();
        c.setDataBreakpoints(Map.of("x", "")); // empty condition = break on any change
        c.launch(RunRequest.ofScriptAndText(MUTATING_SCRIPT, "in"));

        // x=1 (line 3) is the baseline; x becomes 2 on line 4, and the post-statement
        // hook stops on that very line — where the change happened, not the line after.
        assertThat(c.awaitStop(3000)).isTrue();
        assertThat(c.currentLine()).isEqualTo(4);
        assertThat(c.stopReason()).isEqualTo("data breakpoint");
        assertThat(c.stopDetail()).isEqualTo("x changed");
        assertThat(c.stack().get(0).locals()).containsEntry("x", 2);
    }

    @Test
    void dataBreakpoint_continue_stopsAgainOnTheNextChange() {
        DebugController c = new DebugController();
        c.setDataBreakpoints(Map.of("x", ""));
        c.launch(RunRequest.ofScriptAndText(MUTATING_SCRIPT, "in"));
        assertThat(c.awaitStop(3000)).isTrue(); // line 4, x==2

        c.resume();
        // x becomes 3 on line 5 — the post-hook stops there.
        assertThat(c.awaitStop(3000)).isTrue();
        assertThat(c.currentLine()).isEqualTo(5);
        assertThat(c.stack().get(0).locals()).containsEntry("x", 3);
    }

    @Test
    void conditionalDataBreakpoint_stopsOnFalseToTrueEdge_notEveryHit() {
        // x runs 1 → 2 → 3. Condition ">= 3" is false at 1 and 2, true at 3, so the
        // FIRST (and only) stop is the line that makes it true: line 5 (x = 3).
        DebugController c = new DebugController();
        c.setDataBreakpoints(Map.of("x", ">= 3"));
        c.launch(RunRequest.ofScriptAndText(MUTATING_SCRIPT, "in"));

        assertThat(c.awaitStop(3000)).isTrue();
        assertThat(c.currentLine()).isEqualTo(5);
        assertThat(c.stopReason()).isEqualTo("data breakpoint");
        assertThat(c.stopDetail()).isEqualTo("x >= 3");
        assertThat(c.stack().get(0).locals()).containsEntry("x", 3);

        // No further false→true edge → the run finishes.
        c.resume();
        assertThat(c.awaitStop(3000)).isFalse();
        assertThat(c.isFinished()).isTrue();
    }

    @Test
    void conditionalDataBreakpoint_equalsMatchesNumericValue() {
        DebugController c = new DebugController();
        c.setDataBreakpoints(Map.of("x", "== 2"));
        c.launch(RunRequest.ofScriptAndText(MUTATING_SCRIPT, "in"));

        assertThat(c.awaitStop(3000)).isTrue();
        assertThat(c.currentLine()).isEqualTo(4);
        assertThat(c.stopDetail()).isEqualTo("x == 2");
        assertThat(c.stack().get(0).locals()).containsEntry("x", 2);
    }

    // A variable declared inside a block, changed on its last in-scope line — the case
    // that used to slip through: the pre-statement hook only saw the change on the next
    // line, which is already outside the block (return message), so `test` was gone.
    // 1: import ...
    // 2: Message processData(Message message) {
    // 3:     if (message.getBody(String) == 'go') {
    // 4:         def test = 1
    // 5:         message.setProperty('p', 'v')
    // 6:     }
    // 7:     return message
    // 8: }
    private static final String BLOCK_SCOPED_SCRIPT =
            "import com.sap.gateway.ip.core.customdev.util.Message\n"
                    + "Message processData(Message message) {\n"
                    + "    if (message.getBody(String) == 'go') {\n"
                    + "        def test = 1\n"
                    + "        message.setProperty('p', 'v')\n"
                    + "    }\n"
                    + "    return message\n"
                    + "}\n";

    @Test
    void conditionalDataBreakpoint_stopsOnTheDeclaringLine_whileTheVarIsStillInScope() {
        DebugController c = new DebugController();
        c.setDataBreakpoints(Map.of("test", "== 1"));
        c.launch(RunRequest.ofScriptAndText(BLOCK_SCOPED_SCRIPT, "go"));

        assertThat(c.awaitStop(3000)).isTrue();
        assertThat(c.currentLine()).isEqualTo(4); // the `def test = 1` line, not the next line
        assertThat(c.stopReason()).isEqualTo("data breakpoint");
        assertThat(c.stopDetail()).isEqualTo("test == 1");
        assertThat(c.stack().get(0).locals()).containsEntry("test", 1);
    }

    @Test
    void conditionalDataBreakpoint_neverSatisfied_runsUninterrupted() {
        DebugController c = new DebugController();
        c.setDataBreakpoints(Map.of("x", "== 99"));
        c.launch(RunRequest.ofScriptAndText(MUTATING_SCRIPT, "in"));

        assertThat(c.awaitStop(3000)).isFalse();
        assertThat(c.isFinished()).isTrue();
    }

    @Test
    void setLocalOverride_appliedAtTheNextStatement_changesTheRunningValue() {
        // Pause where x==1, override it to 5, then resume: the override is written back
        // before the next statement runs, so `def y = x` sees 5 and the body is "5".
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"  // 1
                        + "Message processData(Message message) {\n"      // 2
                        + "    def x = 1\n"                                // 3
                        + "    def y = x\n"                               // 4  <- breakpoint
                        + "    message.setBody(y.toString())\n"           // 5
                        + "    return message\n"                          // 6
                        + "}\n";
        DebugController c = new DebugController();
        c.setBreakpoints(Set.of(4));
        c.launch(RunRequest.ofScriptAndText(script, "in"));
        assertThat(c.awaitStop(3000)).isTrue();
        assertThat(c.stack().get(0).locals()).containsEntry("x", 1);

        c.setLocalOverride("x", "5");
        c.resume();

        assertThat(c.awaitStop(3000)).isFalse();
        assertThat(c.isFinished()).isTrue();
        assertThat(((Message) c.result()).getBody(String.class)).isEqualTo("5");
    }

    @Test
    void buildResult_capturesPropertiesSetByTheScript() {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) {\n"
                        + "    message.setProperties([country: 'CZ', testMode: 'false'])\n"
                        + "    return message\n"
                        + "}\n";
        DebugController c = new DebugController();
        c.launch(RunRequest.ofScriptAndText(script, "in"));
        assertThat(c.awaitStop(3000)).isFalse(); // no breakpoints → runs to the end
        assertThat(c.isFinished()).isTrue();

        RunResult r = c.buildResult();
        assertThat(r.propertiesAfter()).containsKeys("country", "testMode");
    }

    @Test
    void buildResult_capturesPropertiesParsedFromAQueryHeader() {
        // The exact reported script: parse CamelHttpQuery into properties via a closure.
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "import java.nio.charset.Charset\n"
                        + "Message extractUrlGetParameters(Message message) {\n"
                        + "    String httpQuery = message.getHeader('CamelHttpQuery', String)\n"
                        + "    if (httpQuery) {\n"
                        + "        Map<String, String> queryParameters = URLDecoder.decode(httpQuery, Charset.defaultCharset().name())\n"
                        + "            .replace('$','')\n"
                        + "            .tokenize('&')\n"
                        + "            .collectEntries { it.tokenize('=') }\n"
                        + "        message.setProperties(queryParameters)\n"
                        + "    }\n"
                        + "    return message\n"
                        + "}\n";
        RunRequest req = new RunRequest(
                script, "in".getBytes(StandardCharsets.UTF_8), "text/plain",
                Map.of("CamelHttpQuery", "country=CZ&testMode=false"), Map.of(),
                List.of(), 10_000L, null, List.of(), "extractUrlGetParameters");
        DebugController c = new DebugController();
        c.launch(req);
        assertThat(c.awaitStop(3000)).isFalse();
        assertThat(c.isFinished()).isTrue();
        assertThat(c.exitCause()).isNull();

        RunResult r = c.buildResult();
        assertThat(r.propertiesAfter()).containsEntry("country", "CZ").containsEntry("testMode", "false");
    }

    @Test
    void setMessageHeaderAndProperty_mutateTheLiveMessage() {
        DebugController c = launchAt(Set.of(5));
        c.setMessageHeader("X-Flag", "on");
        c.setMessageProperty("retry", "3");

        assertThat(c.message().getHeaders()).containsEntry("X-Flag", "on");
        assertThat(c.message().getProperties()).containsEntry("retry", "3");
    }

    @Test
    void noDataBreakpoint_runsUninterrupted() {
        DebugController c = new DebugController();
        c.launch(RunRequest.ofScriptAndText(MUTATING_SCRIPT, "in"));
        // No line or data breakpoints → finishes without a pause.
        assertThat(c.awaitStop(3000)).isFalse();
        assertThat(c.isFinished()).isTrue();
    }
}
