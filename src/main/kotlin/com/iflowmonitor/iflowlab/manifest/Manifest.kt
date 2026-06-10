package com.iflowmonitor.iflowlab.manifest

import com.iflowmonitor.iflowlab.model.Expectation
import com.iflowmonitor.iflowlab.model.RoutingMode
import java.nio.file.Path

/** A parsed test suite. [baseDir] is the manifest file's directory — all relative paths resolve here (AC27). */
data class Manifest(
    val baseDir: Path,
    val xslt: String?,
    val mode: RoutingMode?,
    val namespaces: Map<String, String>,
    val tests: List<TestCase>,
)

data class TestCase(
    val name: String,
    val xslt: String?,
    val mode: RoutingMode?,
    val params: Map<String, String>,
    val namespaces: Map<String, String>,
    val body: String?,
    val bodyFile: String?,
    val expectation: Expectation,
)

/** Any manifest parse/validation failure. Surfaces as a non-zero CLI exit (AC1, AC3, AC12). */
class ManifestException(message: String) : RuntimeException(message)
