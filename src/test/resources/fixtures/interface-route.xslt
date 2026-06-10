<?xml version="1.0" encoding="UTF-8"?>
<!-- Interface-only determination: root ns0:Interfaces with a flat Interface (endpoint /pip/ep/i1,
     Name IF_1; Index emitted only when dc_Index='yes'). -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:param name="dc_Index"/>
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Interfaces>
            <Interface>
                <xsl:if test="$dc_Index = 'yes'"><Index>1</Index></xsl:if>
                <Service>/pip/ep/i1</Service>
                <Name>IF_1</Name>
            </Interface>
        </ns0:Interfaces>
    </xsl:template>
</xsl:stylesheet>
