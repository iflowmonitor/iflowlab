package com.iflowmonitor.iflowlab.app;

import com.iflowmonitor.iflowlab.engine.Engine;
import com.iflowmonitor.iflowlab.engine.GroovyRunEngine;
import com.iflowmonitor.iflowlab.engine.RunResult;
import com.iflowmonitor.iflowlab.engine.xslt.XsltEngine;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The workbench run loop: script + input Message → type-aware result envelope (D6, R12). */
@Path("/run")
public class RunResource {

    private final Engine groovy = new GroovyRunEngine();
    private final Engine xslt = new XsltEngine();

    @Inject
    WorkspaceService workspace;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RunResult run(RunRequestDto dto) {
        // Same RunRequest/RunResult contract, engine chosen by kind (D2 swappability).
        Engine engine = "xslt".equalsIgnoreCase(dto.kind()) ? xslt : groovy;
        // Seed the CPI platform-service mocks from the workspace's services.yaml (slice 4).
        return engine.run(dto.toRunRequest(workspace.readServices()));
    }
}
