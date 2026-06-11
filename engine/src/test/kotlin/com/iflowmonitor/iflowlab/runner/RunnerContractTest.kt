package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/** Engine contract (AC5/AC7/AC8): `run` returns data — no printing, no exit codes, no throwing. */
class RunnerContractTest {

    @TempDir
    lateinit var dir: Path

    private fun writeManifest(content: String): Path = Files.writeString(dir.resolve("suite.yaml"), content)

    /** AC5 — running a suite writes NOTHING to stdout/stderr; the result is the only output channel. */
    @Test
    fun runWritesNothingToStdoutOrStderr() {
        Files.copy(fixture("receiver-route.xslt").toPath(), dir.resolve("r.xslt"))
        val m = writeManifest(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: passes
                params: { dc_Route: ONE }
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        val outCapture = ByteArrayOutputStream()
        val errCapture = ByteArrayOutputStream()
        val oldOut = System.out
        val oldErr = System.err
        System.setOut(PrintStream(outCapture, true))
        System.setErr(PrintStream(errCapture, true))
        val result = try {
            RoutingRunner().run(m)
        } finally {
            System.setOut(oldOut)
            System.setErr(oldErr)
        }
        assertTrue(result.cases.single().passed, "suite should pass: $result")
        assertEquals("", outCapture.toString(), "engine must not print to stdout")
        assertEquals("", errCapture.toString(), "engine must not print to stderr")
    }

    /** AC7 — a manifest parse/config failure yields configError + empty cases; run does NOT throw. */
    @Test
    fun badManifestYieldsConfigErrorWithoutThrowing() {
        val m = writeManifest("this: is: not: valid: {[}")
        val result = assertDoesNotThrow<SuiteResult> { RoutingRunner().run(m) }
        assertNotNull(result.configError, "configError must be set")
        assertTrue(result.cases.isEmpty(), "cases must be empty on config error")
    }

    /** AC8 — a stylesheet that throws yields a CaseResult with engineError; run does NOT throw, suite continues. */
    @Test
    fun throwingStylesheetYieldsEngineErrorWithoutThrowing() {
        Files.writeString(
            dir.resolve("boom.xslt"),
            """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <xsl:message terminate="yes">boom</xsl:message>
                </xsl:template>
            </xsl:stylesheet>
            """.trimIndent(),
        )
        Files.copy(fixture("receiver-route.xslt").toPath(), dir.resolve("r.xslt"))
        val m = writeManifest(
            """
            mode: receiver
            tests:
              - name: throws
                xslt: boom.xslt
                expect:
                  receivers: []
              - name: still runs after the throw
                xslt: r.xslt
                params: { dc_Route: ONE }
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        val result = assertDoesNotThrow<SuiteResult> { RoutingRunner().run(m) }
        assertNull(result.configError)
        assertEquals(2, result.cases.size)

        val thrown = result.cases[0]
        assertNotNull(thrown.engineError, "engineError must be set for a throwing stylesheet")
        assertEquals("boom.xslt", thrown.xslt, "the failing stylesheet stays attributed to the case")
        assertTrue(thrown.gateResults.isEmpty())
        assertFalse(thrown.passed)

        assertTrue(result.cases[1].passed, "the suite continues past a throwing case: $result")
    }
}
