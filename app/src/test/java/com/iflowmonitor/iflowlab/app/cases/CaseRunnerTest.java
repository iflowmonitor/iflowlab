package com.iflowmonitor.iflowlab.app.cases;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.app.WorkspaceService;
import com.iflowmonitor.iflowlab.engine.RunResult.Status;
import com.iflowmonitor.iflowlab.engine.assertions.Assertion;
import com.iflowmonitor.iflowlab.engine.assertions.Assertion.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs saved cases against the real Groovy engine (slice 3). */
class CaseRunnerTest {

    private static final String UPPERCASE =
            "import com.sap.gateway.ip.core.customdev.util.Message\n"
                    + "Message processData(Message message) {\n"
                    + "    message.setBody(message.getBody(String.class).toUpperCase())\n"
                    + "    message.setHeader('Content-Type', 'text/plain')\n"
                    + "    return message\n"
                    + "}\n";

    private CaseRunner runnerFor(Path ws) throws Exception {
        Files.createDirectories(ws);
        Files.writeString(ws.resolve("Upper.groovy"), UPPERCASE);
        return new CaseRunner(new WorkspaceService(ws.toString()));
    }

    @Test
    void passingCase_allAssertionsHold(@TempDir Path ws) throws Exception {
        CaseRunner runner = runnerFor(ws);
        RunCase c = new RunCase(
                "happy",
                "Upper.groovy",
                new MessageSpec("hello world", "text/plain", Map.of(), Map.of()),
                List.of(
                        Assertion.of(Kind.STATUS, "OK"),
                        Assertion.of(Kind.BODY_EQUALS, "HELLO WORLD"),
                        Assertion.of(Kind.HEADER, "Content-Type", "text/plain")));

        CaseReport report = runner.run(c);

        assertThat(report.passed()).isTrue();
        assertThat(report.result().status()).isEqualTo(Status.OK);
        assertThat(report.assertions()).extracting(r -> r.passed()).containsExactly(true, true, true);
    }

    @Test
    void failingCase_reportsWhichAssertionFailed(@TempDir Path ws) throws Exception {
        CaseRunner runner = runnerFor(ws);
        RunCase c = new RunCase(
                "sad",
                "Upper.groovy",
                new MessageSpec("hello", "text/plain", Map.of(), Map.of()),
                List.of(
                        Assertion.of(Kind.STATUS, "OK"),
                        Assertion.of(Kind.BODY_EQUALS, "goodbye")));

        CaseReport report = runner.run(c);

        assertThat(report.passed()).isFalse();
        assertThat(report.assertions().get(0).passed()).isTrue();
        assertThat(report.assertions().get(1).passed()).isFalse();
        assertThat(report.assertions().get(1).actual()).isEqualTo("HELLO");
    }

    @Test
    void inlineScriptTextAndServices_runWithoutTouchingTheWorkspace(@TempDir Path ws) throws Exception {
        // Empty workspace — the script is NOT a file; it arrives inline (stateless runner, SaaS R1).
        CaseRunner runner = new CaseRunner(new WorkspaceService(ws.toString()));
        String credScript =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "import com.sap.it.api.ITApiFactory\n"
                        + "import com.sap.it.api.securestore.SecureStoreService\n"
                        + "Message processData(Message message) {\n"
                        + "    def store = ITApiFactory.getService(SecureStoreService.class, null)\n"
                        + "    message.setBody(store.getUserCredential('Api').getUsername())\n"
                        + "    return message\n"
                        + "}\n";
        RunCase c = new RunCase(
                "inline",
                "not-on-disk.groovy",
                new MessageSpec("x", "text/plain", Map.of(), Map.of()),
                List.of(Assertion.of(Kind.BODY_EQUALS, "svc-user")),
                credScript,
                new com.iflowmonitor.iflowlab.app.ServicesDto(
                        List.of(),
                        Map.of("Api", new com.iflowmonitor.iflowlab.app.ServicesDto.CredentialDto("svc-user", "pw"))));

        CaseReport report = runner.run(c);

        assertThat(report.result().status()).isEqualTo(Status.OK);
        assertThat(report.passed()).isTrue();
    }

    @Test
    void runAll_runsEveryCase(@TempDir Path ws) throws Exception {
        CaseRunner runner = runnerFor(ws);
        RunCase pass = new RunCase("p", "Upper.groovy",
                new MessageSpec("x", "text/plain", Map.of(), Map.of()),
                List.of(Assertion.of(Kind.BODY_EQUALS, "X")));
        RunCase fail = new RunCase("f", "Upper.groovy",
                new MessageSpec("x", "text/plain", Map.of(), Map.of()),
                List.of(Assertion.of(Kind.BODY_EQUALS, "y")));

        List<CaseReport> reports = runner.runAll(List.of(pass, fail));

        assertThat(reports).extracting(CaseReport::passed).containsExactly(true, false);
    }
}
