package com.iflowmonitor.iflowlab.app.cases;

import com.iflowmonitor.iflowlab.app.WorkspaceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** Runs saved test cases and reports pass/fail against their assertions (slice 3). */
@Path("/case")
public class CaseResource {

    @Inject
    CaseRunner runner;

    @Inject
    WorkspaceService workspace;

    /** Run an ad-hoc case descriptor (does not need to be saved first). */
    @POST
    @Path("/run")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CaseReport run(RunCase runCase) {
        return runner.run(runCase);
    }

    /** Run every saved case in the workspace — the suite view. */
    @POST
    @Path("/run-all")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CaseReport> runAll() {
        List<RunCase> cases = workspace.listCases().stream().map(workspace::readCase).toList();
        return runner.runAll(cases);
    }
}
