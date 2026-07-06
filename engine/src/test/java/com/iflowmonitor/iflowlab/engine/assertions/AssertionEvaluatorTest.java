package com.iflowmonitor.iflowlab.engine.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.engine.RunResult;
import com.iflowmonitor.iflowlab.engine.RunResult.BodyType;
import com.iflowmonitor.iflowlab.engine.RunResult.BodyView;
import com.iflowmonitor.iflowlab.engine.RunResult.Status;
import com.iflowmonitor.iflowlab.engine.assertions.Assertion.Kind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssertionEvaluatorTest {

    private final AssertionEvaluator evaluator = new AssertionEvaluator();

    private static RunResult okResult(String body, BodyType type, Map<String, Object> headers, Map<String, Object> props) {
        return new RunResult(
                Status.OK,
                new BodyView(type, "text/plain", body.length(), body, false),
                Map.of(),
                headers,
                Map.of(),
                props,
                List.of(),
                null);
    }

    private AssertionResult evalOne(RunResult r, Assertion a) {
        return evaluator.evaluate(r, List.of(a)).get(0);
    }

    @Test
    void status_matches_caseInsensitively() {
        RunResult r = okResult("X", BodyType.TEXT, Map.of(), Map.of());
        assertThat(evalOne(r, Assertion.of(Kind.STATUS, "ok")).passed()).isTrue();
        assertThat(evalOne(r, Assertion.of(Kind.STATUS, "EXCEPTION")).passed()).isFalse();
    }

    @Test
    void bodyEquals_isExact() {
        RunResult r = okResult("HELLO WORLD", BodyType.TEXT, Map.of(), Map.of());
        assertThat(evalOne(r, Assertion.of(Kind.BODY_EQUALS, "HELLO WORLD")).passed()).isTrue();
        AssertionResult miss = evalOne(r, Assertion.of(Kind.BODY_EQUALS, "hello world"));
        assertThat(miss.passed()).isFalse();
        assertThat(miss.actual()).isEqualTo("HELLO WORLD");
    }

    @Test
    void bodyContains_isSubstring() {
        RunResult r = okResult("HELLO WORLD", BodyType.TEXT, Map.of(), Map.of());
        assertThat(evalOne(r, Assertion.of(Kind.BODY_CONTAINS, "WORLD")).passed()).isTrue();
        assertThat(evalOne(r, Assertion.of(Kind.BODY_CONTAINS, "xyz")).passed()).isFalse();
    }

    @Test
    void bodyType_matches() {
        RunResult r = okResult("{}", BodyType.JSON, Map.of(), Map.of());
        assertThat(evalOne(r, Assertion.of(Kind.BODY_TYPE, "json")).passed()).isTrue();
        assertThat(evalOne(r, Assertion.of(Kind.BODY_TYPE, "XML")).passed()).isFalse();
    }

    @Test
    void header_matchesByName() {
        RunResult r = okResult("X", BodyType.TEXT, Map.of("Content-Type", "text/plain"), Map.of());
        assertThat(evalOne(r, Assertion.of(Kind.HEADER, "Content-Type", "text/plain")).passed()).isTrue();
        assertThat(evalOne(r, Assertion.of(Kind.HEADER, "Content-Type", "application/json")).passed()).isFalse();
        // Missing header fails rather than throwing.
        assertThat(evalOne(r, Assertion.of(Kind.HEADER, "Missing", "x")).passed()).isFalse();
    }

    @Test
    void property_matchesByName() {
        RunResult r = okResult("X", BodyType.TEXT, Map.of(), Map.of("Step", "uppercased"));
        assertThat(evalOne(r, Assertion.of(Kind.PROPERTY, "Step", "uppercased")).passed()).isTrue();
        assertThat(evalOne(r, Assertion.of(Kind.PROPERTY, "Step", "other")).passed()).isFalse();
    }

    @Test
    void allPassed_reflectsWholeSuite() {
        RunResult r = okResult("HELLO", BodyType.TEXT, Map.of("H", "v"), Map.of("P", "1"));
        List<AssertionResult> ok = evaluator.evaluate(r, List.of(
                Assertion.of(Kind.STATUS, "OK"),
                Assertion.of(Kind.BODY_EQUALS, "HELLO"),
                Assertion.of(Kind.HEADER, "H", "v"),
                Assertion.of(Kind.PROPERTY, "P", "1")));
        assertThat(evaluator.allPassed(ok)).isTrue();

        List<AssertionResult> mixed = evaluator.evaluate(r, List.of(
                Assertion.of(Kind.STATUS, "OK"),
                Assertion.of(Kind.BODY_EQUALS, "nope")));
        assertThat(evaluator.allPassed(mixed)).isFalse();
        assertThat(mixed).extracting(AssertionResult::passed).containsExactly(true, false);
    }

    @Test
    void emptySuite_vacuouslyPasses() {
        RunResult r = okResult("X", BodyType.TEXT, Map.of(), Map.of());
        assertThat(evaluator.allPassed(evaluator.evaluate(r, List.of()))).isTrue();
    }
}
