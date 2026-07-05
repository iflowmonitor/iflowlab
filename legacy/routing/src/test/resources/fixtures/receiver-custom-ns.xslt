<?xml version="1.0" encoding="UTF-8"?>
<!-- Emits the Receivers root in a NON-default namespace (urn:custom:v1) to exercise ns0 override
     (AC6 per-case, AC15 suite-level). One receiver: BANK_A. -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="urn:custom:v1">
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <Receiver><Service>BANK_A</Service></Receiver>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
