package com.iflowmonitor.iflowlab.app.lint;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** Fidelity lint for the editor: source in, findings out (slice 7). */
@Path("/lint")
public class LintResource {

    private final FidelityLinter linter = new FidelityLinter();

    public record LintRequest(String script) {}

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public List<Finding> lint(LintRequest request) {
        return linter.lint(request.script());
    }
}
