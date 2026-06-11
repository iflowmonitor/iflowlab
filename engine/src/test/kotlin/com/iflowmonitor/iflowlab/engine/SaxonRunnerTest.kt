package com.iflowmonitor.iflowlab.engine

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaxonRunnerTest {

    /** AC4 — `dc_Receiver` binds to the Saxon param of the LITERAL same name; no prefixing/inventing. */
    @Test
    fun bindsDcParamByLiteralName() {
        val result = SaxonRunner.run(
            fixture("receiver-echo.xslt"),
            mapOf("dc_Receiver" to "BANK_A"),
            DUMMY_BODY,
        )
        // The stylesheet echoes $dc_Receiver into Receiver/Service; seeing BANK_A proves the literal bind.
        assertTrue(result.xml.contains("BANK_A"), "emitted XML should echo the bound param: ${result.xml}")
        val root = result.doc.documentElement
        assertTrue(root.localName == "Receivers", "root should be Receivers, was ${root.nodeName}")
    }

    /** A param that does NOT match the stylesheet's literal name must not bind (no auto-prefixing). */
    @Test
    fun wrongParamNameDoesNotBind() {
        val result = SaxonRunner.run(
            fixture("receiver-echo.xslt"),
            mapOf("Receiver" to "BANK_A"), // missing the dc_ prefix the stylesheet declares
            DUMMY_BODY,
        )
        assertTrue(!result.xml.contains("BANK_A"), "unprefixed name must not bind to dc_Receiver: ${result.xml}")
    }
}
