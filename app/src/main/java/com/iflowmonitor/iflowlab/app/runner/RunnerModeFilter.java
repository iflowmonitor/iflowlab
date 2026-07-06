package com.iflowmonitor.iflowlab.app.runner;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Runner mode (SaaS R2, ADR-0001/0002): the jar serves as a stateless execution
 * service behind a control plane. Workspace-touching endpoints are hidden (404 —
 * the runner has no workspace), and when a runner token is configured every
 * request must present it in {@code X-Runner-Token} (defense in depth on top of
 * the internal-only network). Inactive by default — the local product is
 * untouched.
 */
@Provider
public class RunnerModeFilter implements ContainerRequestFilter {

    public static final String TOKEN_HEADER = "X-Runner-Token";

    @ConfigProperty(name = "iflowlab.runner-mode", defaultValue = "false")
    boolean runnerMode;

    // Optional: SmallRye rejects an empty-String default, so absence models "no token".
    @ConfigProperty(name = "iflowlab.runner-token")
    Optional<String> runnerToken;

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (!runnerMode) {
            return;
        }
        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        // No workspace exists in a runner; stateful endpoints simply are not there.
        if (path.startsWith("workspace") || path.equals("case/run-all")) {
            ctx.abortWith(Response.status(Response.Status.NOT_FOUND).build());
            return;
        }
        String token = runnerToken.orElse("");
        if (!token.isBlank() && !token.equals(ctx.getHeaderString(TOKEN_HEADER))) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }
}
