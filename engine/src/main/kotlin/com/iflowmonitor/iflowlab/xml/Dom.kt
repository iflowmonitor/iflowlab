package com.iflowmonitor.iflowlab.xml

import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Namespace-agnostic DOM child traversal by local name. The SAP routing contract qualifies only the
 * root (`ns0:Receivers`) while inner elements are unqualified (PRD §D6 wire example), and the
 * authoritative XSD is not yet in hand (see N1). Matching on local name is robust to either
 * qualified/unqualified emission and avoids brittle prefix assumptions.
 */
object Dom {
    fun childElements(node: Node): List<Element> {
        val kids = node.childNodes
        val out = ArrayList<Element>(kids.length)
        for (i in 0 until kids.length) {
            val k = kids.item(i)
            if (k is Element) out.add(k)
        }
        return out
    }

    fun childElementsNamed(node: Node, localName: String): List<Element> =
        childElements(node).filter { it.localName == localName || it.nodeName == localName }

    fun firstChildNamed(node: Node, localName: String): Element? =
        childElementsNamed(node, localName).firstOrNull()

    fun textOf(e: Element): String = e.textContent.trim()
}
