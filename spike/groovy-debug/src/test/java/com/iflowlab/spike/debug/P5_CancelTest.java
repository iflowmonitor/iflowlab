package com.iflowlab.spike.debug;

import groovy.lang.Binding;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5: a parked or infinitely-looping instrumented script can be terminated from
 * outside without leaking its thread. Cancellation is cooperative: the next hook
 * throws {@link DebugCancelledException}, unwinding the script thread.
 */
class P5_CancelTest {

    private static final String INFINITE =
            "int x = 0\n" +
            "while (true) {\n" +
            "    x = x + 1\n" +   // line 3: a hooked body statement -> cancellable
            "}\n";

    @Test
    void cancelParkedScriptTerminatesThread() throws Exception {
        DebugSession session = new DebugSession(false);
        session.addBreakpoint(11);
        Binding binding = new Binding();
        DebugEngine.RunHandle run =
                DebugEngine.start(Scripts.sample(), "sample.groovy", binding, session);

        assertTrue(session.awaitPause(5, TimeUnit.SECONDS), "never parked");
        assertTrue(run.isThreadAlive());

        session.cancel();

        assertTrue(run.joinScriptThread(2000), "parked thread did not terminate after cancel");
        assertFalse(run.isThreadAlive(), "thread leaked");
        assertInstanceOf(DebugCancelledException.class, session.exitCause(),
                "run should have unwound via DebugCancelledException");
        // The script never reached its final 'result = [...]' assignment.
        assertFalse(binding.hasVariable("result"), "script kept running past cancel");
    }

    @Test
    void cancelInfiniteLoopTerminatesThread() throws Exception {
        DebugSession session = new DebugSession(false);
        Binding binding = new Binding();
        DebugEngine.RunHandle run =
                DebugEngine.start(INFINITE, "infinite.groovy", binding, session);

        // Let it spin through many hook calls without any breakpoint.
        Thread.sleep(150);
        assertTrue(run.isThreadAlive(), "loop should still be running");

        session.cancel();

        assertTrue(run.joinScriptThread(2000), "infinite loop did not terminate after cancel");
        assertFalse(run.isThreadAlive(), "thread leaked");
        assertInstanceOf(DebugCancelledException.class, session.exitCause());
    }
}
