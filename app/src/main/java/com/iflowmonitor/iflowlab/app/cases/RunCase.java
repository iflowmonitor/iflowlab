package com.iflowmonitor.iflowlab.app.cases;

import com.iflowmonitor.iflowlab.app.ServicesDto;
import com.iflowmonitor.iflowlab.engine.assertions.Assertion;
import java.util.List;

/**
 * A saved test case: binds a workspace script to an input message and the
 * expectations that must hold for the run to pass (slice 3). Persisted as
 * {@code cases/<name>.yaml}.
 *
 * <p>{@code scriptText} and {@code services} are runtime-only (never persisted):
 * a control plane sends them inline so the runner needs no workspace (SaaS R1).
 * When {@code scriptText} is present it wins over resolving {@code script} from
 * the workspace; same for {@code services} vs services.yaml.
 */
public record RunCase(
        String name,
        String script,
        MessageSpec message,
        List<Assertion> assertions,
        String scriptText,
        ServicesDto services) {

    /** Back-compat: workspace-resolved case (local product, yaml round-trip). */
    public RunCase(String name, String script, MessageSpec message, List<Assertion> assertions) {
        this(name, script, message, assertions, null, null);
    }
}
