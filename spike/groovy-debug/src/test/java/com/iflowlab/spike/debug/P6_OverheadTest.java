package com.iflowlab.spike.debug;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P6: rough order-of-magnitude overhead of instrumented vs plain execution of
 * the same script. No rigor -- one warm-up, one timed batch, print the factor.
 * The instrumented path also builds a live locals snapshot at every statement
 * (the P3 capture), so this reflects the "full debugger attached" cost.
 */
class P6_OverheadTest {

    private static final int ITERATIONS = 5000;

    @Test
    void instrumentedOverheadIsWithinOrderOfMagnitude() {
        String src = Scripts.sample();

        Script plain = new GroovyShell(new CompilerConfiguration()).parse(src, "sample.groovy");

        CompilerConfiguration cfg = new CompilerConfiguration();
        cfg.addCompilationCustomizers(new InstrumentingCustomizer());
        Script instrumented = new GroovyShell(cfg).parse(src, "sample.groovy");

        // Attach a session with NO breakpoints -> hooks run but never park.
        DebugSession session = new DebugSession(false);
        DebugRuntime.bind(session);
        try {
            long plainMs = time(plain, ITERATIONS);
            long instrMs = time(instrumented, ITERATIONS);

            double factor = instrMs / (double) Math.max(plainMs, 1);
            System.out.printf("P6 overhead: plain=%dms instrumented=%dms  factor=%.1fx (%d iters)%n",
                    plainMs, instrMs, factor, ITERATIONS);

            assertTrue(instrMs >= plainMs, "instrumented should not be faster than plain");
            assertTrue(factor < 100.0,
                    "instrumentation overhead unexpectedly high: " + factor + "x");
        } finally {
            DebugRuntime.unbind();
        }
    }

    private long time(Script script, int iterations) {
        // Warm-up.
        for (int i = 0; i < iterations / 5; i++) {
            script.setBinding(new Binding());
            script.run();
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            script.setBinding(new Binding());
            script.run();
        }
        return (System.nanoTime() - start) / 1_000_000;
    }
}
