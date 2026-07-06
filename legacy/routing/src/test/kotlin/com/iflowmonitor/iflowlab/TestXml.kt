package com.iflowmonitor.iflowlab

import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Parse an XML string into a namespace-aware DOM, mirroring the engine's own parsing. */
fun parseXml(xml: String): Document {
    val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    return dbf.newDocumentBuilder().parse(InputSource(StringReader(xml)))
}

/** Absolute path to a bundled test fixture on the classpath. */
fun fixture(name: String): java.io.File {
    val url = object {}.javaClass.getResource("/fixtures/$name")
        ?: error("missing test fixture: $name")
    return java.io.File(url.toURI())
}
