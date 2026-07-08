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

    private final FidelityLinter groovyLinter = new FidelityLinter();
    private final XsltLinter xsltLinter = new XsltLinter();

    /**
     * {@code language} selects the linter; absent/unknown defaults to Groovy, so
     * existing callers that post {@code {script}} keep working unchanged.
     */
    public record LintRequest(String script, String language) {}

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public List<Finding> lint(LintRequest request) {
        if (request.language() != null && request.language().equalsIgnoreCase("xslt")) {
            return xsltLinter.lint(request.script());
        }
        return groovyLinter.lint(request.script());
    }
}
