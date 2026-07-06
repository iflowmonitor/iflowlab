package com.iflowmonitor.iflowlab.app.cases;

import com.iflowmonitor.iflowlab.engine.RunResult;
import com.iflowmonitor.iflowlab.engine.assertions.AssertionResult;
import java.util.List;

/** The outcome of running one {@link RunCase}: the run envelope plus each assertion's verdict. */
public record CaseReport(String name, boolean passed, RunResult result, List<AssertionResult> assertions) {}
