package com.iflowmonitor.iflowlab.cli

import com.iflowmonitor.iflowlab.gate.GateOutcome
import com.iflowmonitor.iflowlab.runner.CaseResult
import com.iflowmonitor.iflowlab.runner.SuiteResult

/**
 * Renders a [SuiteResult] exactly as the pre-reshape runner printed it (AC11 golden-pinned):
 * LF line endings, double-space separators, identical per-line formats.
 */
fun renderSuite(result: SuiteResult): String {
    val out = StringBuilder()
    if (result.configError != null) {
        out.appendLine("ERROR: ${result.configError}")
        return out.toString()
    }
    result.cases.forEach { renderCase(it, out) }
    return out.toString()
}

private fun renderCase(c: CaseResult, out: StringBuilder) {
    if (c.engineError != null) {
        if (c.xslt == null) {
            // Binding error (missing mode/xslt) — no stylesheet was executed.
            out.appendLine("ERROR  ${c.name}: ${c.engineError}")
        } else {
            // Stylesheet exception.
            out.appendLine("FAIL  ${c.name}  [stylesheet: ${c.xslt}]")
            out.appendLine("      engine error: ${c.engineError}")
        }
        return
    }
    // AC9 — make the dummy-body substitution visible, not silent.
    val label = if (c.headerOnly) "  header-only (dummy body)" else ""
    out.appendLine("${if (c.passed) "PASS" else "FAIL"}  ${c.name}  [stylesheet: ${c.xslt}]$label")
    for (r in c.gateResults) {
        if (r.outcome != GateOutcome.PASS) {
            out.appendLine("      ${r.outcome} ${r.gateName}: ${r.message}")
            r.findings.forEach { out.appendLine("        - $it") }
        }
        r.warnings.forEach { out.appendLine("      ! ${r.gateName}: $it") }
    }
}
