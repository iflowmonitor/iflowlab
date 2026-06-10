package com.iflowmonitor.iflowlab.gate

import com.iflowmonitor.iflowlab.model.Namespaces
import com.iflowmonitor.iflowlab.model.NotDeterminedSpec
import com.iflowmonitor.iflowlab.model.PartySpec
import com.iflowmonitor.iflowlab.model.ReceiverSpec
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

        // Keyed by receiver name. Party (P5) is asserted as a field AFTER name-pairing, not folded
        // into the key — so two receivers sharing a name but differing only by party would collide
        // here (rare in routing output; would need a composite key to fully honour D8's (Party+Service)).
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

            // Per-receiver field matching (party P5, nested interfaces P4) for receivers present on both sides.
            for (exp in ctx.expectation.receivers) {
                val actualRcv = actualReceivers[exp.name] ?: continue // name already reported as missing
                addAll(receiverFieldFindings("receiver '${exp.name}'", exp, actualRcv))
            }

            // notDetermined block (P5): asserted only when declared (AC21/AC22).
            ctx.expectation.notDetermined?.let { addAll(notDeterminedFindings(it, root)) }
        }

        return if (findings.isEmpty()) {
            GateResult(NAME, GateOutcome.PASS, "selection matches (${expectedNames.size} receiver(s))")
        } else {
            GateResult(NAME, GateOutcome.FAIL, "selection mismatch", findings = findings)
        }
    }

    private fun nameOf(receiver: Element): String? =
        Dom.firstChildNamed(receiver, "Service")?.let { Dom.textOf(it) }

    /** Field-level checks for a receiver already matched by name: party (P5) + interfaces (P4). */
    private fun receiverFieldFindings(label: String, exp: ReceiverSpec, actual: Element): List<String> = buildList {
        exp.party?.let { expectedParty ->
            val actualParty = partyOf(actual)
            if (actualParty != expectedParty.toTuple()) {
                add("$label party mismatch: expected ${show(expectedParty)}, got ${actualParty?.let(::show) ?: "none"}")
            }
        }
        exp.interfaces?.let { expectedIfaces ->
            val container = Dom.firstChildNamed(actual, "Interfaces")
            val actualTuples = container?.let { InterfaceMatching.tuplesOf(it) } ?: emptyList()
            addAll(InterfaceMatching.findings("receiver '${exp.name}'", expectedIfaces, actualTuples))
        }
    }

    /** Match a full receiver tuple against an element (name + party + interfaces) — used for defaultReceiver. */
    private fun receiverTupleFindings(label: String, exp: ReceiverSpec, actual: Element): List<String> = buildList {
        val actualName = nameOf(actual)
        if (actualName != exp.name) add("$label name mismatch: expected '${exp.name}', got '${actualName ?: "none"}'")
        addAll(receiverFieldFindings(label, exp, actual))
    }

    private fun notDeterminedFindings(nd: NotDeterminedSpec, root: Element): List<String> = buildList {
        val actualNd = Dom.firstChildNamed(root, "ReceiverNotDetermined")
        if (actualNd == null) {
            add("expected a ReceiverNotDetermined block, none was emitted")
            return@buildList
        }
        nd.type?.let { expectedType ->
            // Exact free-form string compare; NOT enum-validated (AC21).
            val actualType = Dom.firstChildNamed(actualNd, "Type")?.let { Dom.textOf(it) }
            if (actualType != expectedType) {
                add("notDetermined.type mismatch: expected '$expectedType', got '${actualType ?: "none"}'")
            }
        }
        nd.defaultReceiver?.let { expectedDr ->
            val actualDr = Dom.firstChildNamed(actualNd, "DefaultReceiver")
            if (actualDr == null) {
                add("expected a notDetermined.defaultReceiver, none was emitted")
            } else {
                addAll(receiverTupleFindings("notDetermined.defaultReceiver", expectedDr, actualDr))
            }
        }
    }

    /** A party tuple; null agency/scheme means the attribute was absent (distinct from empty value). */
    private data class PartyTuple(val value: String, val agency: String?, val scheme: String?)

    private fun PartySpec.toTuple() = PartyTuple(value, agency, scheme)

    private fun partyOf(receiver: Element): PartyTuple? {
        val party = Dom.firstChildNamed(receiver, "Party") ?: return null
        return PartyTuple(
            value = Dom.textOf(party),
            agency = party.getAttribute("agency").ifEmpty { null },
            scheme = party.getAttribute("scheme").ifEmpty { null },
        )
    }

    private fun show(p: PartySpec): String = show(p.toTuple())

    private fun show(p: PartyTuple): String =
        "{value=${p.value}" + (p.agency?.let { ", agency=$it" } ?: "") + (p.scheme?.let { ", scheme=$it" } ?: "") + "}"

    companion object {
        const val NAME = "selection"
    }
}
