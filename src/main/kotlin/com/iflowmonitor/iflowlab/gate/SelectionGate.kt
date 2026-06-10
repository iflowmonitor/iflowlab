package com.iflowmonitor.iflowlab.gate

import com.iflowmonitor.iflowlab.model.Namespaces
import com.iflowmonitor.iflowlab.xml.Dom

/**
 * Gate 2 — selection match. P1: exact, order-insensitive receiver-NAME set equality against
 * `Receivers/Receiver/Service` (AC12, AC17). Extra OR missing receiver fails. Widened in P4/P5 to
 * full tuples (party, nested interfaces, notDetermined).
 *
 * P2: the emitted root must be `Receivers` in the namespace bound to `ns0` in the effective map
 * (pre-registered to the SAP system URI, overridable per suite/case) — this is where the namespace
 * declaration becomes load-bearing for the assertion layer (AC6/AC7/AC15).
 */
class SelectionGate : Gate {
    override fun evaluate(ctx: GateContext): GateResult {
        val root = ctx.emitted.documentElement
            ?: return GateResult(NAME, GateOutcome.FAIL, "no output document element")

        val ns0 = ctx.namespaces[Namespaces.NS0] ?: Namespaces.SAP_SYSTEM
        if (root.localName != "Receivers" || root.namespaceURI != ns0) {
            return GateResult(
                NAME, GateOutcome.FAIL, "root is not ns0:Receivers in the resolved ns0 namespace",
                findings = listOf("expected {$ns0}Receivers, got {${root.namespaceURI}}${root.localName}"),
            )
        }

        val actual = Dom.childElementsNamed(root, "Receiver")
            .mapNotNull { r -> Dom.firstChildNamed(r, "Service")?.let { Dom.textOf(it) } }
            .toSet()
        val expected = ctx.expectation.receivers.map { it.name }.toSet()

        val missing = expected - actual
        val extra = actual - expected
        return if (missing.isEmpty() && extra.isEmpty()) {
            GateResult(NAME, GateOutcome.PASS, "receiver set matches (${expected.size} receiver(s))")
        } else {
            GateResult(
                NAME, GateOutcome.FAIL, "receiver set mismatch",
                findings = buildList {
                    if (missing.isNotEmpty()) add("missing: ${missing.sorted()}")
                    if (extra.isNotEmpty()) add("extra: ${extra.sorted()}")
                },
            )
        }
    }

    companion object {
        const val NAME = "selection"
    }
}
