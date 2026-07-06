package com.iflowmonitor.iflowlab.engine.debug;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.engine.RunRequest;
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
}
