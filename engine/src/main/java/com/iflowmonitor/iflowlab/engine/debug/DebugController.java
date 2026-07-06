package com.iflowmonitor.iflowlab.engine.debug;

import com.iflowmonitor.iflowlab.cpimock.CapturingMessageLogFactory;
import com.iflowmonitor.iflowlab.engine.RunRequest;
import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.codehaus.groovy.control.CompilerConfiguration;

/**
 * Drives one interactive debug run: compiles the script with statement
 * instrumentation, runs {@code processData} on a worker thread, and exposes
 * breakpoint/step/inspect operations for the DAP layer to call. One controller =
 * one session (single active debug session, per O6).
 */
public final class DebugController {

    private final Set<Integer> breakpoints = new HashSet<>();
    private final Set<String> watches = new HashSet<>();
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final CapturingMessageLogFactory logFactory = new CapturingMessageLogFactory();

    private DebugSession session;
    private Thread worker;
    private volatile Object result;

    public void setBreakpoints(Set<Integer> lines) {
        breakpoints.clear();
        breakpoints.addAll(lines);
        if (session != null) {
            session.setBreakpoints(breakpoints);
        }
    }

    /** Data breakpoints: stop when any of these locals changes value. */
    public void setDataBreakpoints(Set<String> names) {
        watches.clear();
        watches.addAll(names);
        if (session != null) {
            session.setWatches(watches);
        }
    }

    /** Compile + start the script on a worker thread. Returns immediately (script runs until first stop). */
    public void launch(RunRequest request) {
        session = new DebugSession();
        session.setBreakpoints(breakpoints);
        session.setWatches(watches);

        Message message = new Message();
        message.setBody(request.body());
        request.headers().forEach(message::setHeader);
        request.properties().forEach(message::setProperty);

        Binding binding = new Binding();
        binding.setVariable("messageLogFactory", logFactory);
        binding.setVariable("out", new PrintStream(stdout, true, StandardCharsets.UTF_8));

        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(new InstrumentingCustomizer());
        GroovyShell shell = new GroovyShell(DebugController.class.getClassLoader(), binding, cc);
        Script script = shell.parse(request.script());

        DebugSession s = session;
        worker = new Thread(() -> {
            DebugRuntime.bind(s);
            if (request.services() != null) {
                com.sap.it.api.ITApiFactory.bind(request.services().registry());
            }
            try {
                result = script.invokeMethod(request.function(), message);
                s.markFinished(null);
            } catch (Throwable t) {
                s.markFinished(t);
            } finally {
                DebugRuntime.unbind();
                com.sap.it.api.ITApiFactory.unbind();
            }
        }, "iflowlab-debug");
        worker.setDaemon(true);
        worker.start();
    }

    /** Block until the script parks at a breakpoint/step or finishes. @return true if paused. */
    public boolean awaitStop(long timeoutMs) {
        try {
            return session.awaitPause(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void resume() {
        session.resume();
    }

    public void stepOver() {
        session.stepOver();
    }

    public void stepInto() {
        session.stepInto();
    }

    public void stepOut() {
        session.stepOut();
    }

    public void terminate() {
        if (session != null) {
            session.cancel();
        }
    }

    public boolean isPaused() {
        return session != null && session.isPaused();
    }

    public boolean isFinished() {
        return session == null || session.isFinished();
    }

    public Throwable exitCause() {
        return session == null ? null : session.exitCause();
    }

    public int currentLine() {
        return session == null ? 0 : session.currentLine();
    }

    /** Why the run last paused: "breakpoint", "step", or "data breakpoint". */
    public String stopReason() {
        return session == null ? "" : session.stopReason();
    }

    /** For a data breakpoint pause, the name of the local that changed (else ""). */
    public String stopDetail() {
        return session == null ? "" : session.stopDetail();
    }

    /** Call-stack frames, innermost first (valid while paused). */
    public List<StackFrameInfo> stack() {
        return session == null ? List.of() : session.stack();
    }

    public String capturedOutput() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    public CapturingMessageLogFactory logFactory() {
        return logFactory;
    }

    public Object result() {
        return result;
    }
}
