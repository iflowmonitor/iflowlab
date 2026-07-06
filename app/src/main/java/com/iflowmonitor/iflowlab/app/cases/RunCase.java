package com.iflowmonitor.iflowlab.app.cases;

import com.iflowmonitor.iflowlab.engine.assertions.Assertion;
import java.util.List;

/**
 * A saved test case: binds a workspace script to an input message and the
 * expectations that must hold for the run to pass (slice 3). Persisted as
 * {@code cases/<name>.yaml}.
 */
public record RunCase(String name, String script, MessageSpec message, List<Assertion> assertions) {}
