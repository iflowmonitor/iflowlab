package com.iflowlab.spike.debug;

import groovy.lang.Binding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1: instrument a ~50-line script so every statement calls a hook carrying the
 * correct source line number. No pausing -- just verify the trace.
 *
 * <p>Result: line numbers are EXACT everywhere -- top-level {@code run()} body,
 * C-style loop body, script-level methods, and closures (including a closure
 * whose body shares a source line with its enclosing call, e.g. line 45).
 */
class P1_InstrumentationTest {

    @Test
    void everyStatementFiresHookWithCorrectLineNumbers() {
        DebugSession session = new DebugSession(true); // record full trace
        Binding binding = new Binding();

        DebugEngine.runInstrumentedInline(Scripts.sample(), "sample.groovy", binding, session);

        List<Frame> trace = session.trace();
        assertFalse(trace.isEmpty(), "no statements were instrumented");

        List<Integer> lines = trace.stream().map(f -> f.line).toList();
        Map<Integer, Long> byLine = new TreeMap<>();
        for (int l : lines) {
            byLine.merge(l, 1L, Long::sum);
        }
        System.out.println("P1 line->count: " + byLine);

        // Every reported line is inside the 48-line source file.
        for (int l : lines) {
            assertTrue(l >= 1 && l <= 48, "line out of range: " + l);
        }

        // --- Top-level run() body: exact lines ---
        assertTrue(lines.containsAll(List.of(3, 4, 5, 7, 8, 9, 10, 11, 14, 16, 17, 24, 27)),
                "missing top-level statements");

        // C-style loop body runs once per item (4 items).
        assertEquals(4, byLine.get(8));   // def item
        assertEquals(4, byLine.get(9));   // def len = measure(item)
        assertEquals(4, byLine.get(11));  // total += len

        // --- Closures ---
        assertEquals(4, byLine.get(18));  // eachWithIndex body (4 names)
        assertEquals(2, byLine.get(20));  // if-body: running>10 only for gamma & delta

        // --- Script-level method bodies: exact lines ---
        assertEquals(4, byLine.get(36));  // int c = 0    (measure called 4x)
        assertEquals(4, byLine.get(37));  // for (ch ...)
        assertEquals(19, byLine.get(38)); // c++  (5+4+5+5 chars total)
        assertEquals(4, byLine.get(40));  // return c
        assertTrue(lines.contains(44));   // def sb (buildSummary)
        assertTrue(lines.contains(47));   // return sb.toString()

        // The run produced the correct result (execution intact under instrumentation).
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) binding.getVariable("result");
        assertEquals(19, result.get("total"));
        assertEquals(List.of(5, 4, 5, 5), result.get("lengths"));
        assertEquals(List.of(10, 8, 10, 10), result.get("doubled"));
        assertEquals(17, result.get("running"));
        assertEquals("alpha;beta;gamma;delta;total=19", result.get("summary"));
    }
}
