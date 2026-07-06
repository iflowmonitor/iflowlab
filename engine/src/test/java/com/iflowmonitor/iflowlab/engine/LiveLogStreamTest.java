package com.iflowmonitor.iflowlab.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** The run(request, onLog) overload streams each log line live as it is produced (slice 10). */
class LiveLogStreamTest {

    private final GroovyRunEngine engine = new GroovyRunEngine();

    private static RunRequest request(String script) {
        return new RunRequest(script, "x".getBytes(StandardCharsets.UTF_8), "text/plain",
                Map.of(), Map.of(), List.of(), 5_000L, null, List.of());
    }

    @Test
    void streamsMessageLogAndPrintln_live_andStillReturnsThemInResult() {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) {\n"
                        + "    def log = messageLogFactory.getMessageLog(message)\n"
                        + "    log.setStringProperty('Step', 'one')\n"
                        + "    println 'hello from stdout'\n"
                        + "    log.setStringProperty('Step', 'two')\n"
                        + "    return message\n"
                        + "}\n";

        List<RunResult.LogLine> streamed = new CopyOnWriteArrayList<>();
        RunResult result = engine.run(request(script), streamed::add);

        assertThat(result.status()).isEqualTo(RunResult.Status.OK);
        // Every line the run produced was pushed to the live listener…
        assertThat(streamed).extracting(RunResult.LogLine::message)
                .contains("one", "two", "hello from stdout");
        // …and the final result still carries the full log list (back-compat).
        assertThat(result.logs()).extracting(RunResult.LogLine::message)
                .contains("one", "two", "hello from stdout");
    }

    @Test
    void plainRun_withoutListener_isUnaffected() {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) {\n"
                        + "    println 'still works'\n"
                        + "    return message\n"
                        + "}\n";
        RunResult result = engine.run(request(script));
        assertThat(result.status()).isEqualTo(RunResult.Status.OK);
        assertThat(result.logs()).extracting(RunResult.LogLine::message).contains("still works");
    }
}
