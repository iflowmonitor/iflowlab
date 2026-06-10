<?xml version="1.0" encoding="UTF-8"?>
<!-- Echoes dc_Priority into Receiver/Service. Proves YAML int/bool params arrive as strings (AC5). -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:param name="dc_Priority"/>
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <Receiver><Service><xsl:value-of select="$dc_Priority"/></Service></Receiver>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
