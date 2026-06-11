package com.iflowmonitor.iflowlab.cli

import com.iflowmonitor.iflowlab.runner.CaseResult
import com.iflowmonitor.iflowlab.runner.SuiteResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** AC10 — CLI exit codes unchanged: 0 all pass / 1 any case fails / 2 config error. */
class ExitCodeTest {

    @TempDir
    lateinit var dir: Path

    /** Minimal routing stylesheet: emits one receiver named by the dc_Receiver header. */
    private fun seedStylesheet() {
        Files.writeString(
            dir.resolve("r.xslt"),
            """
            <xsl:stylesheet version="3.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:ns0="http://sap.com/xi/XI/System">
                <xsl:param name="dc_Receiver"/>
                <xsl:output method="xml" omit-xml-declaration="yes"/>
                <xsl:template match="/">
                    <ns0:Receivers>
                        <Receiver><Service><xsl:value-of select="${'$'}dc_Receiver"/></Service></Receiver>
                    </ns0:Receivers>
                </xsl:template>
            </xsl:stylesheet>
            """.trimIndent(),
        )
    }

    private fun runCliOn(manifestContent: String): Pair<Int, String> {
        val m = Files.writeString(dir.resolve("suite.yaml"), manifestContent)
        val out = StringBuilder()
        return runCli(arrayOf(m.toString()), out, StringBuilder()) to out.toString()
    }

    /** All cases pass → EXIT_OK (0), through the real run + render + map wiring. */
    @Test
    fun allPassExitsOk() {
        seedStylesheet()
        val (code, output) = runCliOn(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: passes
                params: { dc_Receiver: BANK_A }
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        assertEquals(EXIT_OK, code, output)
    }

    /** Any failing case → EXIT_FAIL (1), even when other cases pass. */
    @Test
    fun anyFailureExitsFail() {
        seedStylesheet()
        val (code, output) = runCliOn(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: passes
                params: { dc_Receiver: BANK_A }
                expect:
                  receivers:
                    - name: BANK_A
              - name: fails
                params: { dc_Receiver: BANK_A }
                expect:
                  receivers:
                    - name: BANK_B
            """.trimIndent(),
        )
        assertEquals(EXIT_FAIL, code, output)
    }

    /** Manifest/config error → EXIT_CONFIG (2) and the ERROR: line on stdout. */
    @Test
    fun configErrorExitsConfig() {
        val (code, output) = runCliOn("this: is: not: valid: {[}")
        assertEquals(EXIT_CONFIG, code, output)
        assertTrue(output.startsWith("ERROR: "), output)
    }

    /** No args → usage on stderr, EXIT_CONFIG, nothing on stdout. */
    @Test
    fun noArgsPrintsUsageAndExitsConfig() {
        val out = StringBuilder()
        val err = StringBuilder()
        val code = runCli(emptyArray(), out, err)
        assertEquals(EXIT_CONFIG, code)
        assertEquals("usage: iflowlab <manifest.yaml>\n", err.toString())
        assertEquals("", out.toString())
    }

    /** Mapping precedence on the typed model: configError wins over everything; any !passed → fail. */
    @Test
    fun exitCodeMappingPrecedence() {
        val failing = CaseResult("c", "r.xslt", headerOnly = false, gateResults = emptyList(), engineError = "boom")
        val passing = CaseResult("p", "r.xslt", headerOnly = false, gateResults = emptyList())
        assertEquals(EXIT_CONFIG, exitCodeFor(SuiteResult(cases = emptyList(), configError = "bad yaml")))
        assertEquals(EXIT_FAIL, exitCodeFor(SuiteResult(cases = listOf(passing, failing))))
        assertEquals(EXIT_OK, exitCodeFor(SuiteResult(cases = listOf(passing))))
        assertEquals(EXIT_OK, exitCodeFor(SuiteResult(cases = emptyList())))
    }
}
