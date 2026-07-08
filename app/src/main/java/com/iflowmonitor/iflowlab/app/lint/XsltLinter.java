package com.iflowmonitor.iflowlab.app.lint;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import org.xml.sax.SAXParseException;

/**
 * Compile-time lint for XSLT: the stylesheet is handed to JAXP exactly as the
 * {@code XsltEngine} would compile it, and every problem the compiler reports —
 * malformed XML, invalid XPath in a {@code select}/{@code test}/{@code match},
 * an unknown {@code xsl:*} element, a duplicate template match, a missing
 * {@code version} — comes back as an editor {@link Finding}. Mirrors {@link
 * FidelityLinter} for Groovy: source in, findings out.
 *
 * <p>The {@link TransformerFactory} runs with secure processing and no external
 * DTD or stylesheet access, matching {@code XsltEngine}, so lint and run agree on
 * what compiles (and a hostile stylesheet can't reach the filesystem or network).
 */
public final class XsltLinter {

    public List<Finding> lint(String script) {
        if (script == null || script.isBlank()) {
            return List.of();
        }
        Collector collector = new Collector();
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            factory.setErrorListener(collector);
            // Compilation only — never transforms, so no input document is needed.
            factory.newTemplates(new StreamSource(new StringReader(script)));
        } catch (TransformerException e) {
            // XSLTC calls the ErrorListener for each problem before throwing a summary
            // ("N error(s) detected"); keep the located findings and drop the summary.
            if (collector.isEmpty()) {
                collector.record(Finding.Severity.ERROR, e);
            }
        } catch (RuntimeException e) {
            // Factory misconfiguration or an unexpected parser failure — surface it
            // rather than swallow it, anchored at the top of the file.
            collector.recordMessage(Finding.Severity.ERROR, 1, 1, cleanup(e.getMessage()));
        }
        return collector.findings();
    }

    /** Accumulates ErrorListener callbacks — XSLTC reports many before it throws. */
    private static final class Collector implements ErrorListener {
        private final List<Finding> out = new ArrayList<>();
        private final Set<String> seen = new LinkedHashSet<>();

        @Override
        public void warning(TransformerException e) {
            record(Finding.Severity.WARNING, e);
        }

        @Override
        public void error(TransformerException e) {
            record(Finding.Severity.ERROR, e);
        }

        @Override
        public void fatalError(TransformerException e) {
            record(Finding.Severity.ERROR, e);
        }

        void record(Finding.Severity severity, TransformerException e) {
            int line = 1;
            int column = 1;
            SourceLocator loc = e.getLocator();
            if (loc != null && loc.getLineNumber() > 0) {
                line = loc.getLineNumber();
                if (loc.getColumnNumber() > 0) {
                    column = loc.getColumnNumber();
                }
            } else if (e.getException() instanceof SAXParseException spe && spe.getLineNumber() > 0) {
                line = spe.getLineNumber();
                if (spe.getColumnNumber() > 0) {
                    column = spe.getColumnNumber();
                }
            }
            recordMessage(severity, line, column, cleanup(e.getMessage()));
        }

        void recordMessage(Finding.Severity severity, int line, int column, String message) {
            // Underline from the reported column to the end of the line: precise column
            // spans aren't available for most compile errors, and Monaco clamps the far
            // end back to the line's real length.
            String key = severity + ":" + line + ":" + column + ":" + message;
            if (seen.add(key)) {
                out.add(new Finding(line, column, END_OF_LINE, severity, "xslt-compile", message));
            }
        }

        boolean isEmpty() {
            return out.isEmpty();
        }

        List<Finding> findings() {
            return out;
        }
    }

    /** Sentinel end column; the editor clamps it to the model line's real length. */
    private static final int END_OF_LINE = 100_000;

    private static String cleanup(String message) {
        if (message == null || message.isBlank()) {
            return "XSLT compile error";
        }
        // XSLTC messages are often multi-line (message + stylesheet excerpt); the first
        // line carries the human-readable cause.
        String first = message.trim();
        int nl = first.indexOf('\n');
        if (nl >= 0) {
            first = first.substring(0, nl).trim();
        }
        return first;
    }
}
