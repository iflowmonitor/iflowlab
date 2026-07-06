package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** P3 — XSD gate runs as an independent gate in receiver/combined modes (AC20, AC26 reinforced). */
class XsdGateRunnerTest {

    @TempDir
    lateinit var dir: Path

    private fun seed(fixtureName: String, asName: String) {
        Files.copy(fixture(fixtureName).toPath(), dir.resolve(asName))
    }

    private fun run(content: String): Pair<Int, String> {
        val m = Files.writeString(dir.resolve("suite.yaml"), content)
        val sb = StringBuilder()
        return RoutingRunner(sb).run(m) to sb.toString()
    }

    /**
     * AC20 — non-conformant output fails the XSD gate INDEPENDENTLY of selection: the case expects
     * zero receivers (selection passes, since the malformed Receiver emits no Service/name), yet the
     * XSD gate fails it → non-zero exit, with the XSD failure visible.
     */
    @Test
    fun malformedOutputFailsXsdIndependentlyOfSelection() {
        seed("malformed-receiver.xslt", "r.xslt")
        val (code, output) = run(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: malformed but selection-empty
                expect:
                  receivers: []
            """.trimIndent(),
        )
        assertEquals(1, code, output)
        assertTrue(output.contains("xsd"), "XSD gate failure should be reported: $output")
    }

    /** A conformant routing output passes both gates → exit 0 (XSD gate does not break the happy path). */
    @Test
    fun conformantOutputPassesBothGates() {
        seed("receiver-route.xslt", "r.xslt")
        val (code, output) = run(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: conformant
                params: { dc_Route: BOTH }
                expect:
                  receivers:
                    - name: BANK_A
                    - name: BANK_B
            """.trimIndent(),
        )
        assertEquals(0, code, output)
    }
}
