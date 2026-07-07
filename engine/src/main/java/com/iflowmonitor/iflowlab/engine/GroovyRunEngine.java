package com.iflowmonitor.iflowlab.engine;

import com.iflowmonitor.iflowlab.cpimock.CapturingMessageLogFactory;
import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;

/**
 * Runs a CPI Groovy script in-process against the clean-room Message mock (D3).
 *
 * <p>Compiled with the {@link ThreadInterrupt} AST transform so loops are
 * interruptible — a plain {@code GroovyShell} has no per-statement hook, so this
 * is what lets a timeout or cancel actually stop a runaway script (including
 * {@code while(true){}}), satisfying the mandated hard watchdog.
 *
 * <p>Each run executes on its own worker thread; the caller waits up to
 * {@code timeoutMs} and interrupts on expiry. {@code println} is captured via a
 * per-run {@code out} binding (no global {@code System.out} redirection, so
 * concurrent runs don't interfere).
 */
public final class GroovyRunEngine implements Engine {

    private static final AtomicInteger RUN_SEQ = new AtomicInteger();
    private static final int INLINE_CAP_BYTES = 256 * 1024;

    @Override
    public RunResult run(RunRequest request) {
        return run(request, line -> {});
    }

    /**
     * Runs the script, streaming each log line to {@code onLog} the moment it is
     * produced (messageLog entries and {@code println} lines), while still
     * returning the complete log list in the {@link RunResult} (slice 10). The
     * plain {@link #run(RunRequest)} passes a no-op listener.
     */
    public RunResult run(RunRequest request, java.util.function.Consumer<RunResult.LogLine> onLog) {
        Message message = seedMessage(request);
        Map<String, Object> headersBefore = RunResultBuilder.snapshot(message.getHeaders());
        Map<String, Object> propertiesBefore = RunResultBuilder.snapshot(message.getProperties());

        CapturingMessageLogFactory logFactory = new CapturingMessageLogFactory(
                entry -> onLog.accept(new RunResult.LogLine("INFO", "messageLog", entry.value())));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        LineTee printTarget = new LineTee(stdout, line -> onLog.accept(new RunResult.LogLine("INFO", "println", line)));

        ExecutorService worker = Executors.newSingleThreadExecutor(namedDaemon());
        try {
            Future<Message> future = worker.submit(invokeScript(request, message, logFactory, printTarget));
            Message out = future.get(request.timeoutMs(), TimeUnit.MILLISECONDS);
            return RunResultBuilder.success(out, headersBefore, propertiesBefore, RunResultBuilder.logs(stdout, logFactory));
        } catch (TimeoutException e) {
            worker.shutdownNow();
            return RunResultBuilder.terminal(
                    RunResult.Status.TIMEOUT, headersBefore, propertiesBefore, RunResultBuilder.logs(stdout, logFactory), null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return RunResultBuilder.terminal(RunResult.Status.EXCEPTION, headersBefore, propertiesBefore,
                    RunResultBuilder.logs(stdout, logFactory), RunResultBuilder.exceptionInfo(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RunResultBuilder.terminal(RunResult.Status.EXCEPTION, headersBefore, propertiesBefore,
                    RunResultBuilder.logs(stdout, logFactory), RunResultBuilder.exceptionInfo(e));
        } finally {
            worker.shutdownNow();
        }
    }

    private Callable<Message> invokeScript(
            RunRequest request, Message message, CapturingMessageLogFactory logFactory, java.io.OutputStream stdout) {
        return () -> {
            Binding binding = new Binding();
            binding.setVariable("messageLogFactory", logFactory);
            // Groovy Script.println prefers a bound "out" over System.out — per-run capture.
            binding.setVariable("out", new PrintStream(stdout, true, StandardCharsets.UTF_8));
            // Bind CPI platform-service mocks (ITApiFactory) on this worker thread only.
            if (request.services() != null) {
                com.sap.it.api.ITApiFactory.bind(request.services().registry());
            }
            try {
                GroovyShell shell = new GroovyShell(classLoader(request), binding, compilerConfig());
                Script script = shell.parse(request.script());
                Object out = script.invokeMethod(request.function(), message);
                return out instanceof Message m ? m : message;
            } finally {
                com.sap.it.api.ITApiFactory.unbind();
            }
        };
    }

    private static ClassLoader classLoader(RunRequest request) {
        if (request.extraClasspath().isEmpty()) {
            return GroovyRunEngine.class.getClassLoader();
        }
        return new java.net.URLClassLoader(
                request.extraClasspath().toArray(new java.net.URL[0]),
                GroovyRunEngine.class.getClassLoader());
    }

    private static CompilerConfiguration compilerConfig() {
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));
        return cc;
    }

    private static Message seedMessage(RunRequest request) {
        Message m = new Message();
        m.setBody(request.body());
        request.headers().forEach(m::setHeader);
        request.properties().forEach(m::setProperty);
        if (!request.attachments().isEmpty()) {
            Map<String, javax.activation.DataHandler> atts = new LinkedHashMap<>();
            for (AttachmentInput a : request.attachments()) {
                atts.put(a.name(), new javax.activation.DataHandler(
                        a.content() == null ? new byte[0] : a.content(), a.contentType()));
            }
            m.setAttachments(atts);
        }
        return m;
    }

    /**
     * Tees script {@code println} output to a backing buffer (read back for the
     * final result) while emitting each completed line to a live listener as soon
     * as a newline arrives (slice 10). Writes on the worker thread only.
     */
    private static final class LineTee extends java.io.OutputStream {
        private final ByteArrayOutputStream backing;
        private final java.util.function.Consumer<String> onLine;
        private final ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();

        LineTee(ByteArrayOutputStream backing, java.util.function.Consumer<String> onLine) {
            this.backing = backing;
            this.onLine = onLine;
        }

        @Override
        public void write(int b) {
            backing.write(b);
            if (b == '\n') {
                emit();
            } else {
                lineBuf.write(b);
            }
        }

        private void emit() {
            String line = RunResultBuilder.stripCr(lineBuf.toString(StandardCharsets.UTF_8));
            lineBuf.reset();
            if (!line.isEmpty()) {
                onLine.accept(line);
            }
        }
    }

    private static ThreadFactory namedDaemon() {
        return runnable -> {
            Thread t = new Thread(runnable, "iflowlab-run-" + RUN_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
