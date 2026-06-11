package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun run(content: String): SuiteResult {
        val m = Files.writeString(dir.resolve("suite.yaml"), content)
        return RoutingRunner().run(m)
    }

    /** AC3 — no resolvable mode (neither suite nor test) fails the case with a "mode required" engineError. */
    @Test
    fun missingModeFailsWithExplicitMessage() {
        seed("receiver-route.xslt", "r.xslt")
        val result = run(
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
        assertNull(result.configError, "a missing mode is a case failure, not a config error")
        val case = result.cases.single()
        assertFalse(case.passed, "missing mode must fail the case: $result")
        assertNotNull(case.engineError)
        assertTrue(case.engineError!!.contains("mode required"), "explicit message expected: $case")
        assertNull(case.xslt, "no stylesheet was executed for a binding-error case")
    }

    /** AC13 — a per-test `xslt` override replaces the suite binding for that case only; reflected per case. */
    @Test
    fun perTestXsltOverrideReflectedPerCase() {
        seed("receiver-route.xslt", "route.xslt")
        seed("receiver-echo.xslt", "echo.xslt")
        val result = run(
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
        assertTrue(result.cases.all { it.passed }, "$result")
        assertEquals("route.xslt", result.cases[0].xslt)
        assertEquals("echo.xslt", result.cases[1].xslt)
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
        val result = run(
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
              - name: plain receiver (suite mode, must not inherit the override)
                params: { dc_Route: ONE }
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        // The override case passes only under mode=combined; the second case runs under the suite's
        // mode=receiver — both passing proves the override is scoped to its own case.
        assertTrue(result.cases.all { it.passed }, "$result")
    }

    /** AC24 — mode=receiver FAILS (shape-consistency) when nested <Interfaces> are emitted, despite XSD-valid. */
    @Test
    fun receiverModeFailsOnNestedInterfaces() {
        seed("combined-route.xslt", "r.xslt")
        val result = run(
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
        val case = result.cases.single()
        assertFalse(case.passed, "$result")
        // It must be the SHAPE gate that fails — not the XSD gate (the output is XSD-valid).
        assertTrue(case.gateResults.any { it.gateName == "shape" && it.failed }, "shape-gate failure expected: $case")
        assertTrue(case.gateResults.none { it.gateName == "xsd" && it.failed }, "XSD gate must pass (output is XSD-valid): $case")
    }
}
