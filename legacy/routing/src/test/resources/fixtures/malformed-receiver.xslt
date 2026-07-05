<?xml version="1.0" encoding="UTF-8"?>
<!-- Emits a SAP-namespace Receivers root whose Receiver is NON-CONFORMANT: it has no required
     <Service> and carries a disallowed <Bogus> element. Schema-invalid against Receivers.xsd, yet
     because no Receiver/Service text exists the selection of receiver NAMES is empty — so a case
     expecting zero receivers would pass selection while the XSD gate fails it. Proves AC20
     independence. -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://sap.com/xi/XI/System">
    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:template match="/">
        <ns0:Receivers>
            <Receiver>
                <Bogus>not a valid Receiver child</Bogus>
            </Receiver>
        </ns0:Receivers>
    </xsl:template>
</xsl:stylesheet>
