package com.iflowmonitor.iflowlab.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class GroovyRunEngineTest {

    private final Engine engine = new GroovyRunEngine();

    @Test
    void runsProcessData_andReturnsTransformedBody() {
        String script =
                """
                import com.sap.gateway.ip.core.customdev.util.Message
                Message processData(Message message) {
                    def body = message.getBody(String.class)
                    message.setBody(body.toUpperCase())
                    return message
                }
                """;

        RunResult result = engine.run(RunRequest.ofScriptAndText(script, "hello"));

        assertThat(result.status()).isEqualTo(RunResult.Status.OK);
        assertThat(result.body().inline()).isEqualTo("HELLO");
    }

    @Test
    void runsANamedEntryFunction_notJustProcessData() {
        String script =
                """
                import com.sap.gateway.ip.core.customdev.util.Message
                Message extractParams(Message message) {
                    message.setBody('via-extractParams')
                    return message
                }
                """;
        RunRequest req = new RunRequest(
                script, "x".getBytes(), "text/plain",
                Map.of(), Map.of(), List.of(), 10_000L, null, List.of(), "extractParams");

        RunResult result = engine.run(req);

        assertThat(result.status()).isEqualTo(RunResult.Status.OK);
        assertThat(result.body().inline()).isEqualTo("via-extractParams");
    }

    @Test
    void classifiesOutput_usingContentTypeHeaderSetByScript() {
        String script =
                """
                import com.sap.gateway.ip.core.customdev.util.Message
                Message processData(Message message) {
                    message.setBody('{"ok":true}')
                    message.setHeader('Content-Type', 'application/json')
                    return message
                }
                """;

        RunResult result = engine.run(RunRequest.ofScriptAndText(script, "x"));

        assertThat(result.body().type()).isEqualTo(RunResult.BodyType.JSON);
        assertThat(result.body().contentType()).isEqualTo("application/json");
    }

    @Test
    void snapshotsHeadersAndProperties_beforeAndAfter() {
        String script =
                """
                import com.sap.gateway.ip.core.customdev.util.Message
                Message processData(Message message) {
                    message.setHeader('Added', 'yes')
                    message.setProperty('touched', true)
                    return message
                }
                """;
        RunRequest req = new RunRequest(
                script, "x".getBytes(), "text/plain",
                Map.of("Seeded", "1"), Map.of("initial", "p"), List.of(), 10_000L);

        RunResult r = engine.run(req);

        assertThat(r.headersBefore()).containsEntry("Seeded", "1").doesNotContainKey("Added");
        assertThat(r.headersAfter()).containsEntry("Seeded", "1").containsEntry("Added", "yes");
        assertThat(r.propertiesBefore()).containsOnlyKeys("initial");
        assertThat(r.propertiesAfter()).containsKeys("initial", "touched");
    }

    @Test
    void capturesPrintlnAndMessageLog() {
        String script =
                """
                import com.sap.gateway.ip.core.customdev.util.Message
                Message processData(Message message) {
                    println 'hello from stdout'
                    def log = messageLogFactory.getMessageLog(message)
                    log.setStringProperty('Step', 'done')
                    return message
                }
                """;

        RunResult r = engine.run(RunRequest.ofScriptAndText(script, "x"));

        assertThat(r.logs()).extracting(RunResult.LogLine::message)
                .contains("hello from stdout", "done");
    }

    @Test
    void mapsUncaughtExceptionToScriptLine() {
        // Throw is on line 4 of the script (1-based).
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) {\n"
                        + "    def x = 1\n"
                        + "    throw new RuntimeException('boom')\n"
                        + "}\n";

        RunResult r = engine.run(RunRequest.ofScriptAndText(script, "x"));

        assertThat(r.status()).isEqualTo(RunResult.Status.EXCEPTION);
        assertThat(r.exception().type()).isEqualTo("java.lang.RuntimeException");
        assertThat(r.exception().message()).isEqualTo("boom");
        assertThat(r.exception().mappedLine()).isEqualTo(4);
    }

    @Test
    void cpiGroovyModules_xmlSlurperAndJsonSlurper_areOnClasspath() {
        // Fidelity guard (R7): groovy-xml / groovy-json are separate modules in Groovy 4.
        String script =
                """
                import com.sap.gateway.ip.core.customdev.util.Message
                import groovy.xml.XmlSlurper
                import groovy.json.JsonSlurper
                Message processData(Message message) {
                    def xml = new XmlSlurper().parseText('<r><v>7</v></r>')
                    def json = new JsonSlurper().parseText('{"n":5}')
                    message.setBody("${xml.v.text()}-${json.n}".toString())
                    return message
                }
                """;

        RunResult r = engine.run(RunRequest.ofScriptAndText(script, "x"));

        assertThat(r.status()).isEqualTo(RunResult.Status.OK);
        assertThat(r.body().inline()).isEqualTo("7-5");
    }

    @Test
    @Timeout(5)
    void interruptsRunawayLoop_onTimeout() {
        String script =
                """
                import com.sap.gateway.ip.core.customdev.util.Message
                Message processData(Message message) {
                    while (true) { }
                    return message
                }
                """;
        RunRequest req = new RunRequest(
                script, "x".getBytes(), "text/plain", Map.of(), Map.of(), List.of(), 500L);

        RunResult r = engine.run(req);

        assertThat(r.status()).isEqualTo(RunResult.Status.TIMEOUT);
    }
}
