package com.iflowmonitor.iflowlab.cli

import com.iflowmonitor.iflowlab.gate.GateOutcome
import com.iflowmonitor.iflowlab.gate.GateResult
import com.iflowmonitor.iflowlab.runner.CaseResult
import com.iflowmonitor.iflowlab.runner.SuiteResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pins every pre-reshape line format the renderer must reproduce (LF, double-space separators). */
class SuiteRendererTest {

    /** Format 1 — manifest/config failure. */
    @Test
    fun rendersConfigError() {
        val s = renderSuite(SuiteResult(cases = emptyList(), configError = "mapping values are not allowed here"))
        assertEquals("ERROR: mapping values are not allowed here\n", s)
    }

    /** Formats 2/3 — binding errors: engineError with no stylesheet executed (xslt == null). */
    @Test
    fun rendersBindingErrorAsSingleErrorLine() {
        val case = CaseResult(
            name = "no mode anywhere",
            xslt = null,
            headerOnly = true,
            gateResults = emptyList(),
            engineError = "mode required (declare 'mode' at suite or test level)",
        )
        assertEquals(
            "ERROR  no mode anywhere: mode required (declare 'mode' at suite or test level)\n",
            renderSuite(SuiteResult(listOf(case))),
        )
    }

    /** Format 4 — stylesheet exception: engineError with the executed stylesheet attributed. */
    @Test
    fun rendersStylesheetExceptionAsFailWithEngineError() {
        val case = CaseResult(
            name = "throws",
            xslt = "boom.xslt",
            headerOnly = true,
            gateResults = emptyList(),
            engineError = "Processing terminated by xsl:message at line 3 in boom.xslt",
        )
        assertEquals(
            "FAIL  throws  [stylesheet: boom.xslt]\n" +
                "      engine error: Processing terminated by xsl:message at line 3 in boom.xslt\n",
            renderSuite(SuiteResult(listOf(case))),
        )
    }

    /** Format 5 (pass) — PASS line with the header-only label when the case declared no body. */
    @Test
    fun rendersPassWithHeaderOnlyLabel() {
        val case = CaseResult(
            name = "header only",
            xslt = "r.xslt",
            headerOnly = true,
            gateResults = listOf(GateResult("selection", GateOutcome.PASS, "selection matches (1 receiver(s))")),
        )
        assertEquals(
            "PASS  header only  [stylesheet: r.xslt]  header-only (dummy body)\n",
            renderSuite(SuiteResult(listOf(case))),
        )
    }

    /** Format 5 (fail) — non-PASS gates print outcome/message/findings; warnings always print. */
    @Test
    fun rendersGateFailuresFindingsAndWarnings() {
        val case = CaseResult(
            name = "mismatch",
            xslt = "r.xslt",
            headerOnly = false,
            gateResults = listOf(
                GateResult("xsd", GateOutcome.PASS, "schema-valid against Receivers.xsd"),
                GateResult(
                    "selection", GateOutcome.FAIL, "selection mismatch",
                    findings = listOf("missing: BANK_B", "unexpected: BANK_C"),
                ),
                GateResult(
                    "xsd", GateOutcome.SKIPPED, "Interfaces XSD not sourced",
                    warnings = listOf("XSD gate pending (O9)"),
                ),
            ),
        )
        assertEquals(
            "FAIL  mismatch  [stylesheet: r.xslt]\n" +
                "      FAIL selection: selection mismatch\n" +
                "        - missing: BANK_B\n" +
                "        - unexpected: BANK_C\n" +
                "      SKIPPED xsd: Interfaces XSD not sourced\n" +
                "      ! xsd: XSD gate pending (O9)\n",
            renderSuite(SuiteResult(listOf(case))),
        )
    }
}
