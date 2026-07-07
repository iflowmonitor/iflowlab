package com.iflowmonitor.iflowlab.engine.xslt;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.engine.RunRequest;
import com.iflowmonitor.iflowlab.engine.RunResult;
import com.iflowmonitor.iflowlab.engine.RunResult.Status;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** XSLT as a second Engine behind the same contract (slice 5). */
class XsltEngineTest {

    private final XsltEngine engine = new XsltEngine();

    private static RunRequest req(String xslt, String xml) {
        return new RunRequest(xslt, xml.getBytes(StandardCharsets.UTF_8), "application/xml",
                Map.of(), Map.of(), List.of(), 5_000L);
    }

    @Test
    void transformsXmlWithStylesheet() {
        String xslt =
                "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>\n"
                        + "  <xsl:output method='xml' omit-xml-declaration='yes'/>\n"
                        + "  <xsl:template match='/order'>\n"
                        + "    <receipt id='{@id}'><total><xsl:value-of select='sum(item/@price)'/></total></receipt>\n"
                        + "  </xsl:template>\n"
                        + "</xsl:stylesheet>\n";
        String xml = "<order id='42'><item price='10'/><item price='5'/></order>";

        RunResult result = engine.run(req(xslt, xml));

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.body().inline()).isEqualTo("<receipt id=\"42\"><total>15</total></receipt>");
        assertThat(result.body().type()).isEqualTo(RunResult.BodyType.XML);
    }

    @Test
    void headersAndPropertiesPassThroughUnchanged() {
        String xslt =
                "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>\n"
                        + "  <xsl:template match='/'><out/></xsl:template>\n"
                        + "</xsl:stylesheet>\n";
        RunRequest r = new RunRequest(xslt, "<a/>".getBytes(StandardCharsets.UTF_8), "application/xml",
                Map.of("H", "v"), Map.of("P", "1"), List.of(), 5_000L);

        RunResult result = engine.run(r);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.headersAfter()).containsEntry("H", "v");
        assertThat(result.propertiesAfter()).containsEntry("P", "1");
    }

    @Test
    void headerIsExposedAsXsltParam_sapParity() {
        // SAP CI parity: a declared <xsl:param name="dc_country"/> is auto-filled from
        // the message header named dc_country (e.g. set by a Content Modifier).
        String xslt =
                "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>\n"
                        + "  <xsl:output method='xml' omit-xml-declaration='yes'/>\n"
                        + "  <xsl:param name='dc_country'/>\n"
                        + "  <xsl:template match='/'><out country='{$dc_country}'/></xsl:template>\n"
                        + "</xsl:stylesheet>\n";
        RunRequest r = new RunRequest(xslt, "<a/>".getBytes(StandardCharsets.UTF_8), "application/xml",
                Map.of("dc_country", "CZ"), Map.of(), List.of(), 5_000L);

        RunResult result = engine.run(r);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.body().inline()).isEqualTo("<out country=\"CZ\"/>");
    }

    @Test
    void headerAndPropertyBothBind_headerWinsOnClash() {
        String xslt =
                "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>\n"
                        + "  <xsl:output method='xml' omit-xml-declaration='yes'/>\n"
                        + "  <xsl:param name='dc_country'/>\n"
                        + "  <xsl:param name='dc_only_prop'/>\n"
                        + "  <xsl:template match='/'><out c='{$dc_country}' p='{$dc_only_prop}'/></xsl:template>\n"
                        + "</xsl:stylesheet>\n";
        RunRequest r = new RunRequest(xslt, "<a/>".getBytes(StandardCharsets.UTF_8), "application/xml",
                Map.of("dc_country", "CZ"), Map.of("dc_country", "SK", "dc_only_prop", "x"), List.of(), 5_000L);

        RunResult result = engine.run(r);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.body().inline()).isEqualTo("<out c=\"CZ\" p=\"x\"/>");
    }

    @Test
    void malformedStylesheet_reportsException() {
        RunResult result = engine.run(req("<xsl:not-a-stylesheet>", "<a/>"));
        assertThat(result.status()).isEqualTo(Status.EXCEPTION);
        assertThat(result.exception()).isNotNull();
    }

    @Test
    void malformedInputXml_reportsException() {
        String xslt =
                "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>\n"
                        + "  <xsl:template match='/'><out/></xsl:template>\n"
                        + "</xsl:stylesheet>\n";
        RunResult result = engine.run(req(xslt, "<unclosed>"));
        assertThat(result.status()).isEqualTo(Status.EXCEPTION);
    }

    @Test
    void externalEntity_isNotExpanded_xxeSafe() {
        // A DTD pulling an external file must not be resolved (secure processing).
        String xslt =
                "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>\n"
                        + "  <xsl:template match='/'><xsl:value-of select='//data'/></xsl:template>\n"
                        + "</xsl:stylesheet>\n";
        String xml = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><doc><data>&xxe;</data></doc>";
        RunResult result = engine.run(req(xslt, xml));
        // Either the parser rejects the DOCTYPE/entity (EXCEPTION) or the entity is empty —
        // in no case is external file content disclosed.
        if (result.status() == Status.OK) {
            assertThat(result.body().inline()).doesNotContain("root:");
        }
    }
}
