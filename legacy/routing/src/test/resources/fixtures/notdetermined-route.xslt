<?xml version="1.0" encoding="UTF-8"?>
<!-- Emits a static ReceiverNotDetermined block (Type from dc_Type, a DefaultReceiver FALLBACK) and
     NO <Receiver> — the zero-receivers / not-determined case (AC21/AC22/AC23). -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:param name="dc_Type"/>
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <ReceiverNotDetermined>
                <Type><xsl:value-of select="$dc_Type"/></Type>
                <DefaultReceiver>
                    <Service>FALLBACK</Service>
                </DefaultReceiver>
            </ReceiverNotDetermined>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
