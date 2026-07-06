package com.iflowmonitor.iflowlab.engine;

import com.iflowmonitor.iflowlab.cpimock.CapturingMessageLogFactory;
import com.iflowmonitor.iflowlab.cpimock.LogEntry;
import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        Message message = seedMessage(request);
        Map<String, Object> headersBefore = snapshot(message.getHeaders());
        Map<String, Object> propertiesBefore = snapshot(message.getProperties());

        CapturingMessageLogFactory logFactory = new CapturingMessageLogFactory();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        ExecutorService worker = Executors.newSingleThreadExecutor(namedDaemon());
        try {
            Future<Message> future = worker.submit(invokeScript(request, message, logFactory, stdout));
            Message out = future.get(request.timeoutMs(), TimeUnit.MILLISECONDS);
            return success(out, headersBefore, propertiesBefore, logs(stdout, logFactory));
        } catch (TimeoutException e) {
            worker.shutdownNow();
            return terminal(RunResult.Status.TIMEOUT, headersBefore, propertiesBefore, logs(stdout, logFactory), null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return terminal(RunResult.Status.EXCEPTION, headersBefore, propertiesBefore,
                    logs(stdout, logFactory), exceptionInfo(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return terminal(RunResult.Status.EXCEPTION, headersBefore, propertiesBefore,
                    logs(stdout, logFactory), exceptionInfo(e));
        } finally {
            worker.shutdownNow();
        }
    }

    private Callable<Message> invokeScript(
            RunRequest request, Message message, CapturingMessageLogFactory logFactory, ByteArrayOutputStream stdout) {
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
                Object out = script.invokeMethod("processData", message);
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

    private static List<RunResult.AttachmentView> attachments(Message out) {
        Map<String, javax.activation.DataHandler> atts = out.getAttachments();
        if (atts == null || atts.isEmpty()) {
            return List.of();
        }
        List<RunResult.AttachmentView> views = new ArrayList<>();
        for (Map.Entry<String, javax.activation.DataHandler> e : atts.entrySet()) {
            byte[] bytes = attachmentBytes(e.getValue());
            RunResult.BodyType type = BodyTypeClassifier.classify(bytes, e.getValue().getContentType());
            boolean truncated = bytes.length > INLINE_CAP_BYTES;
            byte[] shown = truncated ? java.util.Arrays.copyOf(bytes, INLINE_CAP_BYTES) : bytes;
            String inline = type == RunResult.BodyType.BINARY ? hexPreview(shown) : new String(shown, StandardCharsets.UTF_8);
            views.add(new RunResult.AttachmentView(e.getKey(), e.getValue().getContentType(), bytes.length, inline, truncated));
        }
        return views;
    }

    private static byte[] attachmentBytes(javax.activation.DataHandler handler) {
        try (java.io.InputStream in = handler.getInputStream()) {
            return in == null ? new byte[0] : in.readAllBytes();
        } catch (Exception e) {
            Object content = handler.getContent();
            return content == null ? new byte[0] : String.valueOf(content).getBytes(StandardCharsets.UTF_8);
        }
    }

    private static RunResult success(
            Message out, Map<String, Object> headersBefore, Map<String, Object> propertiesBefore, List<RunResult.LogLine> logs) {
        byte[] bytes = bodyBytes(out);
        String contentType = resolveContentType(out);
        RunResult.BodyType type = BodyTypeClassifier.classify(bytes, contentType);
        RunResult.BodyView view = bodyView(bytes, contentType, type);
        return new RunResult(
                RunResult.Status.OK, view, headersBefore, snapshot(out.getHeaders()),
                propertiesBefore, snapshot(out.getProperties()), logs, attachments(out), null);
    }

    private static RunResult terminal(
            RunResult.Status status, Map<String, Object> headersBefore, Map<String, Object> propertiesBefore,
            List<RunResult.LogLine> logs, RunResult.ExceptionInfo exception) {
        return new RunResult(status, null, headersBefore, headersBefore, propertiesBefore, propertiesBefore, logs, exception);
    }

    private static RunResult.BodyView bodyView(byte[] bytes, String contentType, RunResult.BodyType type) {
        boolean truncated = bytes.length > INLINE_CAP_BYTES;
        byte[] shown = truncated ? java.util.Arrays.copyOf(bytes, INLINE_CAP_BYTES) : bytes;
        String inline = type == RunResult.BodyType.BINARY
                ? hexPreview(shown)
                : new String(shown, StandardCharsets.UTF_8);
        return new RunResult.BodyView(type, contentType, bytes.length, inline, truncated);
    }

    private static byte[] bodyBytes(Message out) {
        byte[] b = out.getBody(byte[].class);
        return b == null ? new byte[0] : b;
    }

    private static String resolveContentType(Message out) {
        for (Map.Entry<String, Object> e : out.getHeaders().entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase("Content-Type") && e.getValue() != null) {
                return e.getValue().toString();
            }
        }
        return null;
    }

    private static List<RunResult.LogLine> logs(ByteArrayOutputStream stdout, CapturingMessageLogFactory logFactory) {
        List<RunResult.LogLine> lines = new ArrayList<>();
        String printed = stdout.toString(StandardCharsets.UTF_8);
        if (!printed.isEmpty()) {
            for (String line : printed.split("\n", -1)) {
                if (!line.isEmpty()) {
                    lines.add(new RunResult.LogLine("INFO", "println", stripCr(line)));
                }
            }
        }
        for (LogEntry entry : logFactory.entries()) {
            lines.add(new RunResult.LogLine("INFO", "messageLog", entry.value()));
        }
        return lines;
    }

    private static RunResult.ExceptionInfo exceptionInfo(Throwable cause) {
        Integer mappedLine = null;
        for (StackTraceElement el : cause.getStackTrace()) {
            if (el.getFileName() != null && el.getFileName().endsWith(".groovy") && el.getLineNumber() > 0) {
                mappedLine = el.getLineNumber();
                break;
            }
        }
        return new RunResult.ExceptionInfo(cause.getClass().getName(), cause.getMessage(), mappedLine, stackTraceString(cause));
    }

    private static String stackTraceString(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static String hexPreview(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(bytes.length, 512);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02x", bytes[i]));
            if ((i + 1) % 16 == 0) {
                sb.append('\n');
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static String stripCr(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    private static Map<String, Object> snapshot(Map<String, Object> live) {
        return new LinkedHashMap<>(live);
    }

    private static ThreadFactory namedDaemon() {
        return runnable -> {
            Thread t = new Thread(runnable, "iflowlab-run-" + RUN_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
