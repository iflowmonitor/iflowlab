<?xml version="1.0" encoding="UTF-8"?>
<!-- Echoes the INPUT body's root element local-name into a single Receiver/Service.
     Lets tests prove which body (inline / bodyFile / dummy) the runner actually fed in,
     and that bodyFile resolves against the manifest dir (AC27). -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <Receiver><Service><xsl:value-of select="local-name(/*)"/></Service></Receiver>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
