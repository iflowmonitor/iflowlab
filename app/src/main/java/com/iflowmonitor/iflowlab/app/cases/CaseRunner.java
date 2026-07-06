package com.iflowmonitor.iflowlab.app.cases;

import com.iflowmonitor.iflowlab.app.WorkspaceService;
import com.iflowmonitor.iflowlab.engine.Engine;
import com.iflowmonitor.iflowlab.engine.GroovyRunEngine;
import com.iflowmonitor.iflowlab.engine.RunRequest;
import com.iflowmonitor.iflowlab.engine.RunResult;
import com.iflowmonitor.iflowlab.engine.assertions.AssertionEvaluator;
import com.iflowmonitor.iflowlab.engine.assertions.AssertionResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Runs a saved {@link RunCase}: resolves its script from the workspace, executes it
 * through the {@link Engine} with the case's inline message, and evaluates the
 * assertions against the result (slice 3). The pass/fail oracle is the pure
 * {@link AssertionEvaluator}; this module just wires it to workspace + engine.
 */
@ApplicationScoped
public class CaseRunner {

    private final WorkspaceService workspace;
    private final Engine engine;
    private final AssertionEvaluator evaluator = new AssertionEvaluator();

    @Inject
    public CaseRunner(WorkspaceService workspace) {
        this(workspace, new GroovyRunEngine());
    }

    /** For tests: inject a specific engine. */
    public CaseRunner(WorkspaceService workspace, Engine engine) {
        this.workspace = workspace;
        this.engine = engine;
    }

    public CaseReport run(RunCase runCase) {
        String script = workspace.readScript(runCase.script());
        MessageSpec msg = runCase.message();
        byte[] body = (msg == null || msg.body() == null ? "" : msg.body()).getBytes(StandardCharsets.UTF_8);
        RunRequest request = new RunRequest(
                script,
                body,
                msg == null ? null : msg.contentType(),
                msg == null ? null : msg.headers(),
                msg == null ? null : msg.properties(),
                List.of(),
                0L);

        RunResult result = engine.run(request);
        List<AssertionResult> verdicts = evaluator.evaluate(result, runCase.assertions());
        return new CaseReport(runCase.name(), evaluator.allPassed(verdicts), result, verdicts);
    }

    /** Run every case; the suite passes only if all cases pass. */
    public List<CaseReport> runAll(List<RunCase> cases) {
        return cases.stream().map(this::run).toList();
    }
}
