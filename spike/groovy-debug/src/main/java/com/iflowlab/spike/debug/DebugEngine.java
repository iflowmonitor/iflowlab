package com.iflowlab.spike.debug;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;

/**
 * Minimal engine for the spike: compiles a Groovy script (optionally with
 * statement instrumentation) and runs it on a dedicated worker thread so a
 * driver thread can observe pauses and drive stepping via the {@link DebugSession}.
 */
public final class DebugEngine {

    /** Compile + run WITHOUT instrumentation (baseline for overhead comparison). */
    public static Object runPlain(String scriptText, String name, Binding binding) {
        GroovyShell shell = new GroovyShell(binding, new CompilerConfiguration());
        Script script = shell.parse(scriptText, name);
        return script.run();
    }

    /** Compile WITH instrumentation and run inline on the calling thread (no debugger attached). */
    public static Object runInstrumentedInline(String scriptText, String name, Binding binding, DebugSession session) {
        Script script = instrument(scriptText, name, binding);
        DebugRuntime.bind(session);
        try {
            return script.run();
        } finally {
            DebugRuntime.unbind();
        }
    }

    /** Compile WITH instrumentation and start the script on a worker thread. */
    public static RunHandle start(String scriptText, String name, Binding binding, DebugSession session) {
        Script script = instrument(scriptText, name, binding);
        RunHandle handle = new RunHandle(session);
        Thread worker = new Thread(() -> {
            DebugRuntime.bind(session);
            try {
                Object result = script.run();
                handle.result = result;
                session.markFinished(null);
            } catch (Throwable t) {
                session.markFinished(t);
            } finally {
                DebugRuntime.unbind();
            }
        }, "groovy-debug-script");
        worker.setDaemon(true);
        handle.worker = worker;
        worker.start();
        return handle;
    }

    private static Script instrument(String scriptText, String name, Binding binding) {
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(new InstrumentingCustomizer());
        GroovyShell shell = new GroovyShell(binding, config);
        return shell.parse(scriptText, name);
    }

    /** Handle to a running instrumented script. */
    public static final class RunHandle {
        public final DebugSession session;
        private volatile Thread worker;
        private volatile Object result;

        RunHandle(DebugSession session) {
            this.session = session;
        }

        public Object result() {
            return result;
        }

        public boolean joinScriptThread(long millis) throws InterruptedException {
            Thread w = worker;
            if (w != null) {
                w.join(millis);
                return !w.isAlive();
            }
            return true;
        }

        public boolean isThreadAlive() {
            Thread w = worker;
            return w != null && w.isAlive();
        }
    }
}
