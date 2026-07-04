package com.iflowlab.spike.debug;

import groovy.lang.Binding;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4: step-over semantics = resume until the next statement in the same or a
 * shallower scope, then park. Scope depth is lexical, bumped only at method /
 * closure boundaries.
 *
 * <p>Demonstrated: plain sequence, inside a loop, and into/over a CLOSURE call.
 * Also pins the known limitation: step-over across a direct call to a
 * script-defined METHOD behaves like step-into (static lexical depth cannot
 * model the dynamic call stack -- see PROBE-REPORT).
 */
class P4_StepOverTest {

    private DebugEngine.RunHandle start(DebugSession session) {
        return DebugEngine.start(Scripts.sample(), "sample.groovy", new Binding(), session);
    }

    private void drain(DebugEngine.RunHandle run) throws Exception {
        while (run.isThreadAlive()) {
            run.session.resume();
            if (!run.session.awaitPause(1, TimeUnit.SECONDS)) {
                break;
            }
        }
        run.joinScriptThread(2000);
    }

    @Test
    void stepOverPlainSequence() throws Exception {
        DebugSession session = new DebugSession(false);
        session.addBreakpoint(3); // def total = 0
        DebugEngine.RunHandle run = start(session);

        assertTrue(session.awaitPause(5, TimeUnit.SECONDS));
        assertEquals(3, session.frame().line);

        session.stepOver();
        assertTrue(session.awaitPause(2, TimeUnit.SECONDS));
        assertEquals(4, session.frame().line); // def items

        session.stepOver();
        assertTrue(session.awaitPause(2, TimeUnit.SECONDS));
        assertEquals(5, session.frame().line); // def lengths

        session.stepOver();
        assertTrue(session.awaitPause(2, TimeUnit.SECONDS));
        assertEquals(7, session.frame().line); // for (...)
        drain(run);
    }

    @Test
    void stepOverInsideLoop() throws Exception {
        DebugSession session = new DebugSession(false);
        session.addBreakpoint(10); // lengths << len  (first iteration)
        DebugEngine.RunHandle run = start(session);

        assertTrue(session.awaitPause(5, TimeUnit.SECONDS));
        assertEquals(10, session.frame().line);

        session.stepOver();
        assertTrue(session.awaitPause(2, TimeUnit.SECONDS));
        assertEquals(11, session.frame().line); // total += len, same depth

        // Step over the last body statement -> loop back-edge to next iteration.
        session.stepOver();
        assertTrue(session.awaitPause(2, TimeUnit.SECONDS));
        assertEquals(8, session.frame().line); // def item, next iteration, same depth
        drain(run);
    }

    @Test
    void stepOverAClosureCallSkipsClosureBody() throws Exception {
        DebugSession session = new DebugSession(false);
        session.addBreakpoint(17); // items.eachWithIndex { ... }
        DebugEngine.RunHandle run = start(session);

        assertTrue(session.awaitPause(5, TimeUnit.SECONDS));
        assertEquals(17, session.frame().line);
        assertEquals(1, session.frame().depth);

        session.stepOver(); // must NOT descend into the closure body (depth 2)
        assertTrue(session.awaitPause(2, TimeUnit.SECONDS));
        assertEquals(24, session.frame().line, "step-over dived into the closure body");
        drain(run);
    }

    @Test
    void stepIntoAClosureCallEntersClosureBody() throws Exception {
        DebugSession session = new DebugSession(false);
        session.addBreakpoint(17);
        DebugEngine.RunHandle run = start(session);

        assertTrue(session.awaitPause(5, TimeUnit.SECONDS));
        assertEquals(17, session.frame().line);

        session.stepInto();
        assertTrue(session.awaitPause(2, TimeUnit.SECONDS));
        assertEquals(18, session.frame().line, "step-into did not enter the closure body");
        assertEquals(2, session.frame().depth, "closure body should be one scope deeper");
        drain(run);
    }

    @Test
    void knownLimitation_stepOverMethodCallActsLikeStepInto() throws Exception {
        DebugSession session = new DebugSession(false);
        session.addBreakpoint(9); // def len = measure(item)
        DebugEngine.RunHandle run = start(session);

        assertTrue(session.awaitPause(5, TimeUnit.SECONDS));
        assertEquals(9, session.frame().line);
        assertEquals(1, session.frame().depth);

        session.stepOver();
        assertTrue(session.awaitPause(2, TimeUnit.SECONDS));
        // Static lexical depth: measure()'s body is also depth 1, so step-over
        // stops INSIDE measure (line 36) instead of on line 10. Documented gap.
        assertEquals(36, session.frame().line,
                "expected the documented method-call limitation (dives into measure)");
        drain(run);
    }
}
