package com.iflowlab.spike.debug;

import groovy.lang.Binding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3: while parked, read variable state -- both the script {@link Binding} and
 * local variables. Locals are captured by injecting a live snapshot map at each
 * hook; this test checks run()-body locals, METHOD locals, and CLOSURE locals
 * (the expected hard part).
 */
class P3_VariableStateTest {

    /** Start a run, break at bpLine, return a snapshot of the first pause. */
    private static Frame firstPause(String script, Binding binding, int bpLine, DebugEngine.RunHandle[] out) throws Exception {
        DebugSession session = new DebugSession(false);
        session.addBreakpoint(bpLine);
        DebugEngine.RunHandle run = DebugEngine.start(script, "sample.groovy", binding, session);
        out[0] = run;
        assertTrue(session.awaitPause(5, TimeUnit.SECONDS), "never parked at line " + bpLine);
        return session.frame();
    }

    private static void finish(DebugEngine.RunHandle run) throws Exception {
        // Drain remaining pauses so the run completes and the thread does not leak.
        while (run.isThreadAlive()) {
            run.session.resume();
            if (!run.session.awaitPause(1, TimeUnit.SECONDS)) {
                break;
            }
        }
        run.joinScriptThread(2000);
    }

    @Test
    void bindingVariablesAreReadableWhileParked() throws Exception {
        Binding binding = new Binding();
        binding.setVariable("inputName", "cpi-message"); // e.g. a seeded CPI input
        DebugEngine.RunHandle[] out = new DebugEngine.RunHandle[1];

        firstPause(Scripts.sample(), binding, 11, out);

        // Driver owns the Binding; it can inspect it live while the script parks.
        assertEquals("cpi-message", binding.getVariable("inputName"));
        finish(out[0]);
    }

    @Test
    void runBodyLocalsAreCaptured() throws Exception {
        Binding binding = new Binding();
        DebugEngine.RunHandle[] out = new DebugEngine.RunHandle[1];

        // Break at 'total += len' (line 11), first loop iteration. Hook fires
        // BEFORE the statement, so total is still 0 and lengths already holds [5].
        Frame f = firstPause(Scripts.sample(), binding, 11, out);
        Map<String, Object> locals = f.locals();

        assertEquals(0, locals.get("total"));
        assertEquals(0, locals.get("i"));
        assertEquals("alpha", locals.get("item"));
        assertEquals(5, locals.get("len"));
        assertEquals(List.of(5), locals.get("lengths"));
        assertNotNull(locals.get("items"));
        finish(out[0]);
    }

    @Test
    void methodLocalsAreCaptured() throws Exception {
        Binding binding = new Binding();
        DebugEngine.RunHandle[] out = new DebugEngine.RunHandle[1];

        // Break at 'c++' (line 38) inside measure(String s) -- a method scope.
        // First hit: measuring "alpha", first character, c still 0.
        Frame f = firstPause(Scripts.sample(), binding, 38, out);
        Map<String, Object> locals = f.locals();

        assertEquals("alpha", locals.get("s"), "method parameter not visible");
        assertEquals(0, locals.get("c"), "method local not visible");
        assertTrue(locals.containsKey("ch"), "for-each loop var not visible");
        finish(out[0]);
    }

    @Test
    void closureParamsAreCaptured_butEnclosingLocalsAreNot() throws Exception {
        Binding binding = new Binding();
        DebugEngine.RunHandle[] out = new DebugEngine.RunHandle[1];

        // Break at 'running += name.length()' (line 18) inside the eachWithIndex
        // closure. First hit: name='alpha', idx=0.
        Frame f = firstPause(Scripts.sample(), binding, 18, out);
        Map<String, Object> locals = f.locals();

        // Closure's own parameters ARE captured.
        assertEquals("alpha", locals.get("name"), "closure param not visible");
        assertEquals(0, locals.get("idx"), "closure param not visible");

        // DOCUMENTED WALL: an enclosing (captured) local such as 'running' is NOT
        // in the snapshot -- a synthetic read of it inside the closure would
        // resolve dynamically and blow up, so we deliberately omit it.
        assertFalse(locals.containsKey("running"),
                "enclosing captured locals are not capturable via synthetic reads");
        finish(out[0]);
    }
}
