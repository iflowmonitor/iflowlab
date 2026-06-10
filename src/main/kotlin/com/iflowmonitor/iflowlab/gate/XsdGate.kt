package com.iflowmonitor.iflowlab.gate

import com.iflowmonitor.iflowlab.model.Namespaces
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import javax.xml.XMLConstants
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

/**
 * Gate 1 — XSD validation (AC20). Validates the emitted XML against the official-style
 * `Receivers.xsd` (receiver/combined modes), INDEPENDENTLY of selection matching. A malformed or
 * non-conformant document fails here with the schema's own error, regardless of whether the
 * selected receiver set happens to match.
 *
 * The bundled schema targets the SAP system namespace. If a suite/case overrides `ns0` to a
 * different URI (PRD D3), the SAP schema does not apply to that namespace; the gate then returns
 * SKIPPED with an explicit warning rather than silently passing or spuriously failing — the gate is
 * never silently skipped (same principle as the interface-mode AC25 case).
 */
class XsdGate : Gate {
    override fun evaluate(ctx: GateContext): GateResult {
        val ns0 = ctx.namespaces[Namespaces.NS0] ?: Namespaces.SAP_SYSTEM
        if (ns0 != Namespaces.SAP_SYSTEM) {
            return GateResult(
                NAME, GateOutcome.SKIPPED,
                "XSD gate not applicable",
                warnings = listOf("XSD gate not applicable: ns0 overridden to '$ns0' (no schema for that namespace)"),
            )
        }

        val errors = ArrayList<String>()
        val validator = RECEIVERS_SCHEMA.newValidator().apply {
            errorHandler = object : ErrorHandler {
                override fun warning(e: SAXParseException) { /* ignore warnings */ }
                override fun error(e: SAXParseException) { errors.add(format(e)) }
                override fun fatalError(e: SAXParseException) { errors.add(format(e)) }
            }
        }
        try {
            validator.validate(DOMSource(ctx.emitted))
        } catch (e: SAXParseException) {
            errors.add(format(e))
        } catch (e: Exception) {
            // validate() also declares SAXException + IOException; attribute them to the XSD gate
            // rather than letting them surface as a generic "engine error".
            errors.add(e.message ?: e.javaClass.simpleName)
        }

        return if (errors.isEmpty()) {
            GateResult(NAME, GateOutcome.PASS, "schema-valid against Receivers.xsd")
        } else {
            GateResult(NAME, GateOutcome.FAIL, "schema validation failed", findings = errors)
        }
    }

    private fun format(e: SAXParseException): String =
        "line ${e.lineNumber}:${e.columnNumber} ${e.message}"

    companion object {
        const val NAME = "xsd"

        /** Compiled once; Schema is thread-safe and reusable. */
        private val RECEIVERS_SCHEMA: Schema by lazy {
            val url = XsdGate::class.java.getResource("/schema/Receivers.xsd")
                ?: error("bundled schema /schema/Receivers.xsd not found on classpath")
            SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(url)
        }
    }
}
