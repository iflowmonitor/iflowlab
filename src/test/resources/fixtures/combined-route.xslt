<?xml version="1.0" encoding="UTF-8"?>
<!-- Combined determination: receiver BANK_A with one nested interface (endpoint /pip/ep/a1).
     The interface's <Index> is emitted only when dc_Index='yes', to exercise endpoint-anchored
     identity and the index present/absent asymmetry (AC16/AC18/AC19). -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:param name="dc_Index"/>
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <Receiver>
                <Service>BANK_A</Service>
                <Interfaces>
                    <Interface>
                        <xsl:if test="$dc_Index = 'yes'"><Index>1</Index></xsl:if>
                        <Service>/pip/ep/a1</Service>
                    </Interface>
                </Interfaces>
            </Receiver>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
