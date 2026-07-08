package com.iflowmonitor.iflowlab.app.lint;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.app.lint.Finding.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Compile-time lint over XSLT stylesheets — mirrors the runner's XsltEngine compile. */
class XsltLinterTest {

    private final XsltLinter linter = new XsltLinter();

    @Test
    void validStylesheet_producesNoFindings() {
        String xslt =
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                        + "  <xsl:template match=\"/\">\n"
                        + "    <out><xsl:value-of select=\"/in/name\"/></out>\n"
                        + "  </xsl:template>\n"
                        + "</xsl:stylesheet>\n";
        assertThat(linter.lint(xslt)).isEmpty();
    }

    @Test
    void blankScript_producesNoFindings() {
        assertThat(linter.lint("   ")).isEmpty();
    }

    @Test
    void malformedXml_isFlaggedWithALine() {
        // The <out> element is never closed — a well-formedness error.
        String xslt =
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                        + "  <xsl:template match=\"/\">\n"
                        + "    <out>\n"
                        + "  </xsl:template>\n"
                        + "</xsl:stylesheet>\n";
        List<Finding> findings = linter.lint(xslt);
        assertThat(findings).isNotEmpty();
        Finding f = findings.get(0);
        assertThat(f.severity()).isEqualTo(Severity.ERROR);
        assertThat(f.rule()).isEqualTo("xslt-compile");
        assertThat(f.line()).isGreaterThan(0);
        assertThat(f.endColumn()).isGreaterThan(f.column());
    }

    @Test
    void invalidXpath_isFlagged() {
        // Unbalanced parenthesis in the select expression — an XPath compile error.
        String xslt =
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                        + "  <xsl:template match=\"/\">\n"
                        + "    <xsl:value-of select=\"concat('a', 'b'\"/>\n"
                        + "  </xsl:template>\n"
                        + "</xsl:stylesheet>\n";
        List<Finding> findings = linter.lint(xslt);
        assertThat(findings).isNotEmpty();
        assertThat(findings).allSatisfy(f -> assertThat(f.rule()).isEqualTo("xslt-compile"));
        assertThat(findings).anySatisfy(f -> assertThat(f.severity()).isEqualTo(Severity.ERROR));
    }
}
