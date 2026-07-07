package com.iflowmonitor.iflowlab.engine;

import com.iflowmonitor.iflowlab.cpimock.CapturingMessageLogFactory;
import com.iflowmonitor.iflowlab.cpimock.LogEntry;
import com.sap.gateway.ip.core.customdev.util.Message;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link RunResult} from a finished run's Message, captured output and
 * outcome. Extracted from {@link GroovyRunEngine} so the interactive debugger
 * (which drives the script itself) produces the <em>identical</em> result
 * envelope as a plain run — one source of truth for body classification,
 * header/property diffs, attachments and log capture.
 */
public final class RunResultBuilder {

    private static final int INLINE_CAP_BYTES = 256 * 1024;

    private RunResultBuilder() {}

    /** A successful run: body + after-snapshots diffed against the before-snapshots. */
    public static RunResult success(
            Message out,
            Map<String, Object> headersBefore,
            Map<String, Object> propertiesBefore,
            List<RunResult.LogLine> logs) {
        byte[] bytes = bodyBytes(out);
        String contentType = resolveContentType(out);
        RunResult.BodyType type = BodyTypeClassifier.classify(bytes, contentType);
        RunResult.BodyView view = bodyView(bytes, contentType, type);
        return new RunResult(
                RunResult.Status.OK, view, headersBefore, snapshot(out.getHeaders()),
                propertiesBefore, snapshot(out.getProperties()), logs, attachments(out), null);
    }

    /** A run that timed out or threw: no body, before-snapshots echoed as after. */
    public static RunResult terminal(
            RunResult.Status status,
            Map<String, Object> headersBefore,
            Map<String, Object> propertiesBefore,
            List<RunResult.LogLine> logs,
            RunResult.ExceptionInfo exception) {
        return new RunResult(status, null, headersBefore, headersBefore, propertiesBefore, propertiesBefore, logs, exception);
    }

    /** Merge captured {@code println} output and MessageLog entries into ordered log lines. */
    public static List<RunResult.LogLine> logs(ByteArrayOutputStream stdout, CapturingMessageLogFactory logFactory) {
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

    public static RunResult.ExceptionInfo exceptionInfo(Throwable cause) {
        Integer mappedLine = null;
        for (StackTraceElement el : cause.getStackTrace()) {
            if (el.getFileName() != null && el.getFileName().endsWith(".groovy") && el.getLineNumber() > 0) {
                mappedLine = el.getLineNumber();
                break;
            }
        }
        return new RunResult.ExceptionInfo(cause.getClass().getName(), cause.getMessage(), mappedLine, stackTraceString(cause));
    }

    public static Map<String, Object> snapshot(Map<String, Object> live) {
        return new LinkedHashMap<>(live);
    }

    // ---- body / attachment rendering ----

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

    static String stripCr(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }
}
