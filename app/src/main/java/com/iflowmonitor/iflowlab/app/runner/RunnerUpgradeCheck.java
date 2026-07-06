package com.iflowmonitor.iflowlab.app.runner;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The WebSocket twin of {@link RunnerModeFilter}: in runner mode with a token
 * configured, the {@code /run/stream} and {@code /debug} upgrades must carry
 * {@code X-Runner-Token}. JAX-RS filters do not see WebSocket handshakes, so
 * this is a websockets-next {@link HttpUpgradeCheck}.
 */
@ApplicationScoped
public class RunnerUpgradeCheck implements HttpUpgradeCheck {

    @ConfigProperty(name = "iflowlab.runner-mode", defaultValue = "false")
    boolean runnerMode;

    // Optional: SmallRye rejects an empty-String default, so absence models "no token".
    @ConfigProperty(name = "iflowlab.runner-token")
    Optional<String> runnerToken;

    @Override
    public Uni<CheckResult> perform(HttpUpgradeContext ctx) {
        String token = runnerToken.orElse("");
        if (!runnerMode || token.isBlank()) {
            return CheckResult.permitUpgrade();
        }
        String presented = ctx.httpRequest().headers().get(RunnerModeFilter.TOKEN_HEADER);
        return token.equals(presented) ? CheckResult.permitUpgrade() : CheckResult.rejectUpgrade(401);
    }
}
