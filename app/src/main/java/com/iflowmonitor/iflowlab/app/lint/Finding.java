package com.iflowmonitor.iflowlab.app.lint;

/**
 * One fidelity-lint finding: a span on a source line flagged because it would not
 * behave in the workbench the way it does on a real CPI tenant (slice 7).
 */
public record Finding(int line, int column, int endColumn, Severity severity, String rule, String message) {

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }
}
