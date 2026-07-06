package com.iflowmonitor.iflowlab.gate

import com.iflowmonitor.iflowlab.model.Expectation
import com.iflowmonitor.iflowlab.model.RoutingMode
import org.w3c.dom.Document

/**
 * THE LOAD-BEARING SEAM. A pass gate over a case's emitted XML. Every later phase adds a gate as one
 * new implementation; the runner and existing gates never change (design-an-interface synthesis,
 * see routing-mvp-JOURNAL.md). Single SAM by intent.
 */
fun interface Gate {
    fun evaluate(ctx: GateContext): GateResult
}

/** Immutable per-case input handed to every gate. [emitted] is read-only by contract. */
data class GateContext(
    val caseName: String,
    val mode: RoutingMode,
    val emitted: Document,
    val expectation: Expectation,
    val namespaces: Map<String, String>,
)

enum class GateOutcome { PASS, FAIL, SKIPPED }

/**
 * A gate's verdict. FAIL fails the case; PASS and SKIPPED do not. [warnings] surface regardless of
 * outcome (e.g. AC25 "XSD gate pending (O9)" rides on a SKIPPED result).
 */
data class GateResult(
    val gateName: String,
    val outcome: GateOutcome,
    val message: String,
    val findings: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val failed: Boolean get() = outcome == GateOutcome.FAIL
}
