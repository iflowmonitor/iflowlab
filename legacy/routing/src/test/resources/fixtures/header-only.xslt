<?xml version="1.0" encoding="UTF-8"?>
<!-- A header-only routing stylesheet: routes purely on a dc_ header, never touches the input body.
     Runs fine against the injected dummy body (AC8). Emits a fixed receiver. -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:param name="dc_Receiver"/>
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <Receiver><Service><xsl:value-of select="$dc_Receiver"/></Service></Receiver>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
