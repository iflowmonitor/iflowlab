package com.iflowmonitor.iflowlab.app;

import com.iflowmonitor.iflowlab.engine.Engine;
import com.iflowmonitor.iflowlab.engine.GroovyRunEngine;
import com.iflowmonitor.iflowlab.engine.RunResult;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The workbench run loop: script + input Message → type-aware result envelope (D6, R12). */
@Path("/run")
public class RunResource {

    private final Engine engine = new GroovyRunEngine();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RunResult run(RunRequestDto dto) {
        return engine.run(dto.toRunRequest());
    }
}
