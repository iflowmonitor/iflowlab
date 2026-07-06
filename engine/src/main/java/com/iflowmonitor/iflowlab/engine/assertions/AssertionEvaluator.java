package com.iflowmonitor.iflowlab.engine.assertions;

import com.iflowmonitor.iflowlab.engine.RunResult;
import java.util.List;

/**
 * Evaluates a run-case's assertions against a {@link RunResult}. A pure, deeply
 * testable module: no I/O, no engine coupling — a {@code RunResult} in, a verdict
 * per assertion out. This is the oracle behind saved test suites (slice 3).
 */
public final class AssertionEvaluator {

    /** Verdicts in the same order as the given assertions. */
    public List<AssertionResult> evaluate(RunResult result, List<Assertion> assertions) {
        return assertions.stream().map(a -> evaluateOne(result, a)).toList();
    }

    /** True only if every assertion held (an empty list vacuously passes). */
    public boolean allPassed(List<AssertionResult> results) {
        return results.stream().allMatch(AssertionResult::passed);
    }

    private AssertionResult evaluateOne(RunResult result, Assertion a) {
        String expected = a.expected() == null ? "" : a.expected();
        return switch (a.kind()) {
            case STATUS -> {
                String actual = result.status().name();
                yield new AssertionResult(a, actual.equalsIgnoreCase(expected), actual);
            }
            case BODY_EQUALS -> {
                String actual = bodyInline(result);
                yield new AssertionResult(a, actual.equals(expected), actual);
            }
            case BODY_CONTAINS -> {
                String actual = bodyInline(result);
                yield new AssertionResult(a, actual.contains(expected), actual);
            }
            case BODY_TYPE -> {
                String actual = result.body() == null ? "" : result.body().type().name();
                yield new AssertionResult(a, actual.equalsIgnoreCase(expected), actual);
            }
            case HEADER -> {
                String actual = str(result.headersAfter().get(a.target()));
                yield new AssertionResult(a, actual != null && actual.equals(expected), actual);
            }
            case PROPERTY -> {
                String actual = str(result.propertiesAfter().get(a.target()));
                yield new AssertionResult(a, actual != null && actual.equals(expected), actual);
            }
        };
    }

    private static String bodyInline(RunResult result) {
        return result.body() == null || result.body().inline() == null ? "" : result.body().inline();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
