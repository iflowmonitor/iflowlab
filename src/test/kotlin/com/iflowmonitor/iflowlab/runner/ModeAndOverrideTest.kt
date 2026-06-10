package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** P6 — explicit mode (AC3), per-test xslt/mode overrides (AC13/AC14), shape-consistency (AC24). */
class ModeAndOverrideTest {

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

    /** AC3 — no resolvable mode (neither suite nor test) fails non-zero with a "mode required" message. */
    @Test
    fun missingModeFailsWithExplicitMessage() {
        seed("receiver-route.xslt", "r.xslt")
        val (code, output) = run(
            """
            xslt: r.xslt
            tests:
              - name: no mode anywhere
                params: { dc_Route: ONE }
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        assertTrue(code != 0, "missing mode must be non-zero: $output")
        assertTrue(output.contains("mode required"), "explicit message expected: $output")
    }

    /** AC13 — a per-test `xslt` override replaces the suite binding for that case only; reflected in output. */
    @Test
    fun perTestXsltOverrideReflectedInOutput() {
        seed("receiver-route.xslt", "route.xslt")
        seed("receiver-echo.xslt", "echo.xslt")
        val (code, output) = run(
            """
            xslt: route.xslt
            mode: receiver
            tests:
              - name: uses suite stylesheet
                params: { dc_Route: ONE }
                expect:
                  receivers:
                    - name: BANK_A
              - name: overrides stylesheet
                xslt: echo.xslt
                params: { dc_Receiver: OVERRIDE_RCV }
                expect:
                  receivers:
                    - name: OVERRIDE_RCV
            """.trimIndent(),
        )
        assertEquals(0, code, output)
        assertTrue(output.contains("[stylesheet: route.xslt]"), output)
        assertTrue(output.contains("[stylesheet: echo.xslt]"), output)
    }

    /**
     * AC14 — a per-test `mode` override (co-varying with an `xslt` override) drives gate selection for
     * that case: combined output with nested interfaces passes only because mode=combined is honoured
     * (under the suite's mode=receiver the shape gate would fail it).
     */
    @Test
    fun perTestModeOverrideDrivesGateSelection() {
        seed("receiver-route.xslt", "route.xslt")
        seed("combined-route.xslt", "combined.xslt")
        val (code, output) = run(
            """
            xslt: route.xslt
            mode: receiver
            tests:
              - name: combined override
                xslt: combined.xslt
                mode: combined
                params: { dc_Index: "yes" }
                expect:
                  receivers:
                    - name: BANK_A
                      interfaces:
                      - { endpoint: /pip/ep/a1, index: "1" }
            """.trimIndent(),
        )
        assertEquals(0, code, output)
    }

    /** AC24 — mode=receiver FAILS (shape-consistency) when nested <Interfaces> are emitted, despite XSD-valid. */
    @Test
    fun receiverModeFailsOnNestedInterfaces() {
        seed("combined-route.xslt", "r.xslt")
        val (code, output) = run(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: receiver mode but nested interfaces
                params: { dc_Index: "yes" }
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        assertEquals(1, code, output)
        assertTrue(output.contains("shape") || output.contains("Interfaces"), "shape failure expected: $output")
    }
}
