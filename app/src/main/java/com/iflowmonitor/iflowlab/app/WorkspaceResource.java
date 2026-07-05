package com.iflowmonitor.iflowlab.app;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** Read access to workspace scripts and message fixtures (R5). */
@Path("/workspace")
public class WorkspaceResource {

    @Inject
    WorkspaceService workspace;

    public record WorkspaceInfo(String root, List<String> scripts, List<String> messages) {}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WorkspaceInfo info() {
        return new WorkspaceInfo(workspace.root().toString(), workspace.listScripts(), workspace.listMessages());
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
}
