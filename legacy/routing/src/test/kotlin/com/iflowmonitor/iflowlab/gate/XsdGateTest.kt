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

class XsdGateTest {

    private fun ctx(emitted: String, ns: Map<String, String> = Namespaces.effective(emptyMap(), emptyMap())) =
        GateContext(
            caseName = "t",
            mode = RoutingMode.RECEIVER,
            emitted = parseXml(emitted),
            expectation = Expectation(listOf(ReceiverSpec("BANK_A"))),
            namespaces = ns,
        )

    /** AC20 — a conformant Receivers document passes the XSD gate. */
    @Test
    fun conformantOutputPasses() {
        val r = XsdGate().evaluate(
            ctx("<ns0:Receivers xmlns:ns0='$NS'><Receiver><Service>BANK_A</Service></Receiver></ns0:Receivers>"),
        )
        assertEquals(GateOutcome.PASS, r.outcome, r.findings.toString())
    }

    /** AC20 — a Receiver missing required Service (+ a disallowed child) fails the XSD gate. */
    @Test
    fun nonConformantOutputFails() {
        val r = XsdGate().evaluate(
            ctx("<ns0:Receivers xmlns:ns0='$NS'><Receiver><Bogus/></Receiver></ns0:Receivers>"),
        )
        assertEquals(GateOutcome.FAIL, r.outcome)
        assertTrue(r.findings.isNotEmpty(), "should carry a schema-validation error")
    }

    /** When ns0 is overridden away from the SAP namespace, the SAP schema can't apply → SKIPPED + warning. */
    @Test
    fun overriddenNs0SkipsWithWarning() {
        val r = XsdGate().evaluate(
            ctx(
                "<x:Receivers xmlns:x='urn:custom:v1'><Receiver><Service>BANK_A</Service></Receiver></x:Receivers>",
                ns = Namespaces.effective(emptyMap(), mapOf("ns0" to "urn:custom:v1")),
            ),
        )
        assertEquals(GateOutcome.SKIPPED, r.outcome)
        assertTrue(r.warnings.any { it.contains("not applicable") }, r.warnings.toString())
    }
}
