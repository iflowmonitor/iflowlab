package com.iflowmonitor.iflowlab.gate

import com.iflowmonitor.iflowlab.model.RoutingMode
import com.iflowmonitor.iflowlab.xml.Dom

/**
 * Shape-consistency check on the ACTUAL emitted output (PRD D10, AC24). The declared mode must match
 * the emitted structure — a check the XSD gate alone cannot provide, because `Receivers.xsd` permits
 * optional nested `<Interfaces>`.
 *
 * - `receiver` mode: FAIL if any `Receiver` emits nested `<Interfaces>` (use `combined` instead).
 * - `combined` mode: nested interfaces are expected — no shape failure here.
 * - `interface` mode: root/shape handled by the interface-mode pipeline (P8).
 */
class ShapeConsistencyGate : Gate {
    override fun evaluate(ctx: GateContext): GateResult {
        val root = ctx.emitted.documentElement
            ?: return GateResult(NAME, GateOutcome.FAIL, "no output document element")

        return when (ctx.mode) {
            RoutingMode.RECEIVER -> {
                val withInterfaces = Dom.childElementsNamed(root, "Receiver")
                    .count { Dom.firstChildNamed(it, "Interfaces") != null }
                if (withInterfaces > 0) {
                    GateResult(
                        NAME, GateOutcome.FAIL,
                        "mode=receiver but the output emits nested <Interfaces>",
                        findings = listOf(
                            "$withInterfaces receiver(s) carry <Interfaces>; declare 'mode: combined' for nested interfaces",
                        ),
                    )
                } else {
                    GateResult(NAME, GateOutcome.PASS, "shape consistent with mode=receiver")
                }
            }

            RoutingMode.COMBINED -> GateResult(NAME, GateOutcome.PASS, "nested interfaces permitted in mode=combined")
            RoutingMode.INTERFACE -> GateResult(NAME, GateOutcome.PASS, "interface-mode shape handled separately")
        }
    }

    companion object {
        const val NAME = "shape"
    }
}
