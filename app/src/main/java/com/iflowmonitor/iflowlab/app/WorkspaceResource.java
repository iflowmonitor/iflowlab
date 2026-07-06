package com.iflowmonitor.iflowlab.app;

import com.iflowmonitor.iflowlab.app.cases.RunCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/** Read access to workspace scripts, message fixtures, and run-cases (R5, slice 3). */
@Path("/workspace")
public class WorkspaceResource {

    @Inject
    WorkspaceService workspace;

    public record WorkspaceInfo(String root, List<String> scripts, List<String> messages, List<String> cases) {}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WorkspaceInfo info() {
        return new WorkspaceInfo(
                workspace.root().toString(), workspace.listScripts(), workspace.listMessages(), workspace.listCases());
    }

    @GET
    @Path("/script")
    @Produces(MediaType.TEXT_PLAIN)
    public String script(@QueryParam("path") String path) {
        return workspace.readScript(path);
    }

    @GET
    @Path("/message")
    @Produces(MediaType.APPLICATION_JSON)
    public MessageFixture message(@QueryParam("name") String name) {
        return workspace.readMessage(name);
    }

    public record SaveMessageDto(
            String name, String body, String contentType, Map<String, Object> headers, Map<String, Object> properties) {}

    @POST
    @Path("/message")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public MessageFixture saveMessage(SaveMessageDto dto) {
        workspace.saveMessage(dto.name(), dto.body(), dto.contentType(), dto.headers(), dto.properties());
        return workspace.readMessage(dto.name());
    }

    @GET
    @Path("/case")
    @Produces(MediaType.APPLICATION_JSON)
    public RunCase readCase(@QueryParam("name") String name) {
        return workspace.readCase(name);
    }

    @POST
    @Path("/case")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RunCase saveCase(RunCase runCase) {
        workspace.saveCase(runCase);
        return workspace.readCase(runCase.name());
    }
}
