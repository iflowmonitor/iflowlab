<?xml version="1.0" encoding="UTF-8"?>
<!-- Routes by dc_Route: ONE -> {BANK_A}; BOTH -> {BANK_A, BANK_B} (emitted B-then-A to exercise
     order-insensitive matching, AC17); anything else -> zero receivers. -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:param name="dc_Route"/>
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <xsl:if test="$dc_Route = 'BOTH'">
                <Receiver><Service>BANK_B</Service></Receiver>
            </xsl:if>
            <xsl:if test="$dc_Route = 'ONE' or $dc_Route = 'BOTH'">
                <Receiver><Service>BANK_A</Service></Receiver>
            </xsl:if>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
