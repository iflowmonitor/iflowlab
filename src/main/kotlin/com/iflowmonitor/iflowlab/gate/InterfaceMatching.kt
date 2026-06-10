package com.iflowmonitor.iflowlab.gate

import com.iflowmonitor.iflowlab.model.InterfaceSpec
import com.iflowmonitor.iflowlab.xml.Dom
import org.w3c.dom.Element

/**
 * Shared interface set-matching, reused by combined-mode nested interfaces (under a `Receiver`) and
 * interface-mode flat interfaces (directly under the root). Exact-set, order-insensitive, full tuples
 * `{endpoint, index?, name?}`, endpoint-anchored. null index/name means the element was absent
 * (distinct from a value) — so present/absent asymmetry fails (AC18/AC19).
 */
internal object InterfaceMatching {

    data class IfaceTuple(val endpoint: String, val index: String?, val name: String?)

    /** The `<Interface>` children directly under [container] (a `Receiver/Interfaces` or the `Interfaces` root). */
    fun tuplesOf(container: Element): List<IfaceTuple> =
        Dom.childElementsNamed(container, "Interface").map { iface ->
            IfaceTuple(
                endpoint = Dom.firstChildNamed(iface, "Service")?.let { Dom.textOf(it) } ?: "",
                index = Dom.firstChildNamed(iface, "Index")?.let { Dom.textOf(it) },
                name = Dom.firstChildNamed(iface, "Name")?.let { Dom.textOf(it) },
            )
        }

    fun findings(label: String, expected: List<InterfaceSpec>, actual: List<IfaceTuple>): List<String> {
        val expectedSet = expected.map { IfaceTuple(it.endpoint, it.index, it.name) }.toSet()
        val actualSet = actual.toSet()
        val missing = expectedSet - actualSet
        val extra = actualSet - expectedSet
        return buildList {
            if (missing.isNotEmpty()) add("$label missing interface(s): ${missing.map(::show)}")
            if (extra.isNotEmpty()) add("$label extra interface(s): ${extra.map(::show)}")
        }
    }

    private fun show(t: IfaceTuple): String =
        "{endpoint=${t.endpoint}" + (t.index?.let { ", index=$it" } ?: "") + (t.name?.let { ", name=$it" } ?: "") + "}"
}
