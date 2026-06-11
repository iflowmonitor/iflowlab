package com.iflowmonitor.iflowlab.gate

import com.iflowmonitor.iflowlab.model.Namespaces

/**
 * Interface-only mode gate 1 (P8). The standalone Interfaces XSD is unsourced (O9), so this gate
 * applies NO schema validation but emits the literal `XSD gate pending (O9)` warning — the XSD gate
 * is never silently skipped (AC25). Replace with a real validating gate once the Step05 schema lands.
 */
class InterfaceXsdPendingGate : Gate {
    override fun evaluate(ctx: GateContext): GateResult =
        GateResult(
            gateName = "xsd",
            outcome = GateOutcome.SKIPPED,
            message = PENDING,
            warnings = listOf(PENDING),
        )

    companion object {
        const val PENDING = "XSD gate pending (O9)"
    }
}

/**
 * Interface-only mode selection gate (P8). Root must be `ns0:Interfaces` (in the resolved ns0
 * namespace); flat `Interface` tuples are matched exact-set, order-insensitive, endpoint-anchored —
 * the same rules as combined-mode nested interfaces ([InterfaceMatching]).
 */
class InterfaceSelectionGate : Gate {
    override fun evaluate(ctx: GateContext): GateResult {
        val root = ctx.emitted.documentElement
            ?: return GateResult(NAME, GateOutcome.FAIL, "no output document element")

        val ns0 = ctx.namespaces[Namespaces.NS0] ?: Namespaces.SAP_SYSTEM
        if (root.localName != "Interfaces" || root.namespaceURI != ns0) {
            return GateResult(
                NAME, GateOutcome.FAIL, "root is not ns0:Interfaces in the resolved ns0 namespace",
                findings = listOf("expected {$ns0}Interfaces, got {${root.namespaceURI}}${root.localName}"),
            )
        }

        val expected = ctx.expectation.interfaces ?: emptyList()
        val findings = InterfaceMatching.findings("interfaces", expected, InterfaceMatching.tuplesOf(root))
        return if (findings.isEmpty()) {
            GateResult(NAME, GateOutcome.PASS, "interface set matches (${expected.size} interface(s))")
        } else {
            GateResult(NAME, GateOutcome.FAIL, "interface selection mismatch", findings = findings)
        }
    }

    companion object {
        const val NAME = "selection"
    }
}
