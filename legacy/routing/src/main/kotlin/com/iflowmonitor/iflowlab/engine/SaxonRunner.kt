package com.iflowmonitor.iflowlab.engine

import net.sf.saxon.s9api.Processor
import net.sf.saxon.s9api.QName
import net.sf.saxon.s9api.Serializer
import net.sf.saxon.s9api.XdmAtomicValue
import net.sf.saxon.s9api.XdmValue
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.stream.StreamSource

/** Result of running a stylesheet: the serialized emitted XML plus a parsed (namespace-aware) DOM. */
data class EmittedResult(val xml: String, val doc: Document)

/**
 * Runs a routing stylesheet on Saxon-HE 9.9.1-x (PRD D11), injecting `dc_` params by their LITERAL
 * name (AC4) as string-typed values (AC5), feeding the given input body, and capturing the emitted
 * routing XML. A fresh transformer is created per run (s9api: params are fixed once invoked).
 */
object SaxonRunner {
    private val processor = Processor(false)

    fun run(xsltFile: File, params: Map<String, String>, body: String): EmittedResult {
        val executable = processor.newXsltCompiler().compile(StreamSource(xsltFile))
        val transformer = executable.load30()

        if (params.isNotEmpty()) {
            val bound = HashMap<QName, XdmValue>(params.size)
            for ((name, value) in params) bound[QName(name)] = XdmAtomicValue(value)
            transformer.setStylesheetParameters(bound)
        }

        val source = processor.newDocumentBuilder().build(StreamSource(StringReader(body)))
        val writer = StringWriter()
        val serializer = processor.newSerializer(writer)
        serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes")
        transformer.applyTemplates(source, serializer)

        val xml = writer.toString()
        return EmittedResult(xml, parse(xml))
    }

    private fun parse(xml: String): Document {
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return dbf.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }
}
