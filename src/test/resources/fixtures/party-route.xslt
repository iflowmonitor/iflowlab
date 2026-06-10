<?xml version="1.0" encoding="UTF-8"?>
<!-- Emits one receiver carrying a Party (value + agency/scheme attributes), to exercise party as
     part of receiver identity (PRD D8, AC22 full-tuple rules). -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <Receiver>
                <Party agency="A1" scheme="S1">PARTY_V</Party>
                <Service>BANK_A</Service>
            </Receiver>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
