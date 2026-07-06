package com.iflowmonitor.iflowlab.engine.xslt;

import com.iflowmonitor.iflowlab.engine.BodyTypeClassifier;
import com.iflowmonitor.iflowlab.engine.Engine;
import com.iflowmonitor.iflowlab.engine.RunRequest;
import com.iflowmonitor.iflowlab.engine.RunResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
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
import javax.xml.XMLConstants;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * A second {@link Engine} behind the same contract (D2): the {@code script} is an XSLT
 * stylesheet and the message {@code body} is the XML input; the transform output becomes
 * the new body. Headers/properties pass through unchanged (an XSLT step maps the payload).
 *
 * <p>The {@link TransformerFactory} runs with secure processing and no external DTD or
 * stylesheet access, so a hostile stylesheet cannot reach the filesystem or network (XXE).
 * The transform runs on a worker thread with the request timeout, mirroring the Groovy
 * engine's watchdog.
 */
public final class XsltEngine implements Engine {

    private static final AtomicInteger RUN_SEQ = new AtomicInteger();
    private static final int INLINE_CAP_BYTES = 256 * 1024;

    /** Rethrow parse/transform problems as exceptions instead of the JAXP default stderr dump. */
    private static final ErrorListener THROWING = new ErrorListener() {
        @Override
        public void warning(TransformerException e) {
            // warnings are non-fatal; ignore
        }

        @Override
        public void error(TransformerException e) throws TransformerException {
            throw e;
        }

        @Override
        public void fatalError(TransformerException e) throws TransformerException {
            throw e;
        }
    };

    @Override
    public RunResult run(RunRequest request) {
        Map<String, Object> headers = snapshot(request.headers());
        Map<String, Object> properties = snapshot(request.properties());

        ExecutorService worker = Executors.newSingleThreadExecutor(namedDaemon());
        try {
            Future<byte[]> future = worker.submit(transform(request));
            byte[] output = future.get(request.timeoutMs(), TimeUnit.MILLISECONDS);
            RunResult.BodyType type = BodyTypeClassifier.classify(output, null);
            return new RunResult(
                    RunResult.Status.OK, bodyView(output, type),
                    headers, headers, properties, properties, List.of(), null);
        } catch (TimeoutException e) {
            worker.shutdownNow();
            return terminal(RunResult.Status.TIMEOUT, headers, properties, null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return terminal(RunResult.Status.EXCEPTION, headers, properties, exceptionInfo(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return terminal(RunResult.Status.EXCEPTION, headers, properties, exceptionInfo(e));
        } finally {
            worker.shutdownNow();
        }
    }

    private static Callable<byte[]> transform(RunRequest request) {
        return () -> {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            factory.setErrorListener(THROWING);
            Transformer transformer = factory.newTransformer(new StreamSource(new StringReader(request.script())));
            transformer.setErrorListener(THROWING);
            byte[] input = request.body() == null ? new byte[0] : request.body();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new StreamSource(new ByteArrayInputStream(input)), new StreamResult(out));
            return out.toByteArray();
        };
    }

    private static RunResult.BodyView bodyView(byte[] bytes, RunResult.BodyType type) {
        boolean truncated = bytes.length > INLINE_CAP_BYTES;
        byte[] shown = truncated ? java.util.Arrays.copyOf(bytes, INLINE_CAP_BYTES) : bytes;
        return new RunResult.BodyView(type, null, bytes.length, new String(shown, StandardCharsets.UTF_8), truncated);
    }

    private static RunResult terminal(
            RunResult.Status status, Map<String, Object> headers, Map<String, Object> properties,
            RunResult.ExceptionInfo exception) {
        return new RunResult(status, null, headers, headers, properties, properties, List.of(), exception);
    }

    private static RunResult.ExceptionInfo exceptionInfo(Throwable cause) {
        Integer line = null;
        if (cause instanceof TransformerException te && te.getLocator() != null && te.getLocator().getLineNumber() > 0) {
            line = te.getLocator().getLineNumber();
        }
        return new RunResult.ExceptionInfo(cause.getClass().getName(), cause.getMessage(), line, stackTrace(cause));
    }

    private static String stackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static Map<String, Object> snapshot(Map<String, Object> live) {
        return new LinkedHashMap<>(live);
    }

    private static ThreadFactory namedDaemon() {
        return runnable -> {
            Thread t = new Thread(runnable, "iflowlab-xslt-" + RUN_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
