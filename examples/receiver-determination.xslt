<?xml version="1.0" encoding="UTF-8"?>
<!-- Example routing stylesheet: routes by the dc_Receiver header.
     BANK_A -> {BANK_A}; BOTH -> {BANK_A, BANK_B}; anything else -> no receiver. -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:param name="dc_Receiver"/>
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <xsl:if test="$dc_Receiver = 'BANK_A' or $dc_Receiver = 'BOTH'">
                <Receiver><Service>BANK_A</Service></Receiver>
            </xsl:if>
            <xsl:if test="$dc_Receiver = 'BOTH'">
                <Receiver><Service>BANK_B</Service></Receiver>
            </xsl:if>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
