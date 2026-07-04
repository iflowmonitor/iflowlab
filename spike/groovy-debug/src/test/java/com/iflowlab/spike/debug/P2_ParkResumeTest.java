package com.iflowlab.spike.debug;

import groovy.lang.Binding;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2: a breakpoint hook parks the script thread; the driver thread observes the
 * pause, then resumes it. The script must complete correctly after resume.
 */
class P2_ParkResumeTest {

    @Test
    void breakpointParksThreadAndResumeCompletesRun() throws Exception {
        DebugSession session = new DebugSession(false);
        session.addBreakpoint(11); // 'total += len' inside the for-loop

        Binding binding = new Binding();
        DebugEngine.RunHandle run =
                DebugEngine.start(Scripts.sample(), "sample.groovy", binding, session);

        // Driver observes the pause.
        assertTrue(session.awaitPause(5, TimeUnit.SECONDS), "script never parked");
        assertTrue(session.isPaused());
        assertFalse(session.isFinished(), "run should still be in progress while parked");
        assertEquals(11, session.frame().line, "parked at the wrong line");

        // The script thread is genuinely blocked: it does not finish on its own.
        assertFalse(run.joinScriptThread(300), "thread advanced past breakpoint without resume");
        assertTrue(run.isThreadAlive());

        // Resume -> it hits line 11 again on the next iterations; resume through all.
        int resumes = 0;
        while (session.isPaused()) {
            session.resume();
            resumes++;
            session.awaitPause(2, TimeUnit.SECONDS); // re-parks on next iteration or finishes
            if (resumes > 10) {
                break; // safety
            }
        }
        assertEquals(4, resumes, "breakpoint on line 11 should hit once per loop iteration");

        assertTrue(run.joinScriptThread(2000), "script did not finish after resume");
        assertTrue(session.isFinished());
        assertEquals(null, session.exitCause(), "run threw unexpectedly");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) binding.getVariable("result");
        assertEquals(19, result.get("total"));
        assertEquals("alpha;beta;gamma;delta;total=19", result.get("summary"));
    }
}
