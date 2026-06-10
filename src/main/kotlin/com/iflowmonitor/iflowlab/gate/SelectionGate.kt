package com.iflowmonitor.iflowlab.gate

import com.iflowmonitor.iflowlab.model.InterfaceSpec
import com.iflowmonitor.iflowlab.model.Namespaces
import com.iflowmonitor.iflowlab.xml.Dom
import org.w3c.dom.Element

/**
 * Gate 2 — selection match.
 *
 * - Receivers: exact, order-insensitive set equality on receiver NAME (`Receiver/Service`) — extra OR
 *   missing receiver fails (AC12, AC17).
 * - Combined mode (P4): a receiver whose expectation carries a nested `interfaces` list also has its
 *   `Receiver/Interfaces/Interface` matched as an exact-set of full tuples `{endpoint, index?, name?}`,
 *   order-insensitive, identity anchored on `endpoint`. Index/Name are asserted by present-or-absent
 *   plus value: expected-omits-index must match actual-omits-Index, and vice versa fails (AC18, AC19).
 *
 * P2: the emitted root must be `Receivers` in the namespace bound to `ns0` (AC6/AC7/AC15).
 * Widened further in P5 (party, notDetermined).
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

        // Keyed by receiver name; routing output has unique receiver names. (P5 widens identity to
        // (party + name); duplicate bare names would collide here — revisit when party lands.)
        val actualReceivers = Dom.childElementsNamed(root, "Receiver")
            .mapNotNull { r -> nameOf(r)?.let { it to r } }
            .toMap()
        val actualNames = actualReceivers.keys
        val expectedNames = ctx.expectation.receivers.map { it.name }.toSet()

        val findings = buildList {
            val missing = expectedNames - actualNames
            val extra = actualNames - expectedNames
            if (missing.isNotEmpty()) add("missing receiver(s): ${missing.sorted()}")
            if (extra.isNotEmpty()) add("extra receiver(s): ${extra.sorted()}")

            // Per-receiver nested-interface matching (combined mode) for receivers present on both sides.
            for (exp in ctx.expectation.receivers) {
                val expectedIfaces = exp.interfaces ?: continue
                val actualRcv = actualReceivers[exp.name] ?: continue // name already reported as missing
                addAll(interfaceFindings(exp.name, expectedIfaces, extractInterfaces(actualRcv)))
            }
        }

        return if (findings.isEmpty()) {
            GateResult(NAME, GateOutcome.PASS, "selection matches (${expectedNames.size} receiver(s))")
        } else {
            GateResult(NAME, GateOutcome.FAIL, "selection mismatch", findings = findings)
        }
    }

    private fun nameOf(receiver: Element): String? =
        Dom.firstChildNamed(receiver, "Service")?.let { Dom.textOf(it) }

    /** A full interface tuple; null index/name means the element was absent (distinct from a value). */
    private data class IfaceTuple(val endpoint: String, val index: String?, val name: String?)

    private fun InterfaceSpec.toTuple() = IfaceTuple(endpoint, index, name)

    private fun extractInterfaces(receiver: Element): List<IfaceTuple> {
        val container = Dom.firstChildNamed(receiver, "Interfaces") ?: return emptyList()
        return Dom.childElementsNamed(container, "Interface").map { iface ->
            IfaceTuple(
                endpoint = Dom.firstChildNamed(iface, "Service")?.let { Dom.textOf(it) } ?: "",
                index = Dom.firstChildNamed(iface, "Index")?.let { Dom.textOf(it) },
                name = Dom.firstChildNamed(iface, "Name")?.let { Dom.textOf(it) },
            )
        }
    }

    private fun interfaceFindings(
        receiverName: String,
        expected: List<InterfaceSpec>,
        actual: List<IfaceTuple>,
    ): List<String> {
        val expectedSet = expected.map { it.toTuple() }.toSet()
        val actualSet = actual.toSet()
        val missing = expectedSet - actualSet
        val extra = actualSet - expectedSet
        return buildList {
            if (missing.isNotEmpty()) add("receiver '$receiverName' missing interface(s): ${missing.map(::show)}")
            if (extra.isNotEmpty()) add("receiver '$receiverName' extra interface(s): ${extra.map(::show)}")
        }
    }

    private fun show(t: IfaceTuple): String =
        "{endpoint=${t.endpoint}" + (t.index?.let { ", index=$it" } ?: "") + (t.name?.let { ", name=$it" } ?: "") + "}"

    companion object {
        const val NAME = "selection"
    }
}
