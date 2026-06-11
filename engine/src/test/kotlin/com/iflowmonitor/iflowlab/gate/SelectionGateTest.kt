package com.iflowmonitor.iflowlab.gate

import com.iflowmonitor.iflowlab.model.Expectation
import com.iflowmonitor.iflowlab.model.Namespaces
import com.iflowmonitor.iflowlab.model.ReceiverSpec
import com.iflowmonitor.iflowlab.model.RoutingMode
import com.iflowmonitor.iflowlab.parseXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val NS = "http://sap.com/xi/XI/System"

class SelectionGateTest {

    private fun ctx(emitted: String, expected: List<String>) = GateContext(
        caseName = "t",
        mode = RoutingMode.RECEIVER,
        emitted = parseXml(emitted),
        expectation = Expectation(expected.map { ReceiverSpec(it) }),
        // Exercise the same effective-namespace path production uses (ns0 pre-registered), not a bare map.
        namespaces = Namespaces.effective(emptyMap(), emptyMap()),
    )

    /** AC17 — receiver match is order-insensitive: emitted order differs from expected, sets equal. */
    @Test
    fun orderInsensitiveSetMatchPasses() {
        val r = SelectionGate().evaluate(
            ctx(
                "<ns0:Receivers xmlns:ns0='$NS'>" +
                    "<Receiver><Service>BANK_B</Service></Receiver>" +
                    "<Receiver><Service>BANK_A</Service></Receiver></ns0:Receivers>",
                listOf("BANK_A", "BANK_B"),
            ),
        )
        assertEquals(GateOutcome.PASS, r.outcome, r.message)
    }

    /** AC17 — a missing receiver fails. */
    @Test
    fun missingReceiverFails() {
        val r = SelectionGate().evaluate(
            ctx(
                "<ns0:Receivers xmlns:ns0='$NS'><Receiver><Service>BANK_A</Service></Receiver></ns0:Receivers>",
                listOf("BANK_A", "BANK_B"),
            ),
        )
        assertEquals(GateOutcome.FAIL, r.outcome)
        assertTrue(r.findings.any { it.contains("BANK_B") }, "should report the missing receiver")
    }

    /** AC17 — an extra receiver fails. */
    @Test
    fun extraReceiverFails() {
        val r = SelectionGate().evaluate(
            ctx(
                "<ns0:Receivers xmlns:ns0='$NS'>" +
                    "<Receiver><Service>BANK_A</Service></Receiver>" +
                    "<Receiver><Service>BANK_B</Service></Receiver></ns0:Receivers>",
                listOf("BANK_A"),
            ),
        )
        assertEquals(GateOutcome.FAIL, r.outcome)
        assertTrue(r.findings.any { it.contains("BANK_B") }, "should report the extra receiver")
    }

    /** AC23 precursor — empty expected set passes only when no receiver emitted. */
    @Test
    fun zeroReceiversMatchesEmptyOutput() {
        val r = SelectionGate().evaluate(ctx("<ns0:Receivers xmlns:ns0='$NS'/>", emptyList()))
        assertEquals(GateOutcome.PASS, r.outcome, r.message)
    }
}
