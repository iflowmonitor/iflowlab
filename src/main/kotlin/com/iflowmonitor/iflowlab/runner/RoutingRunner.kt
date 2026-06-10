package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.engine.DUMMY_BODY
import com.iflowmonitor.iflowlab.engine.SaxonRunner
import com.iflowmonitor.iflowlab.gate.Gate
import com.iflowmonitor.iflowlab.gate.GateContext
import com.iflowmonitor.iflowlab.gate.GateOutcome
import com.iflowmonitor.iflowlab.gate.GateResult
import com.iflowmonitor.iflowlab.gate.SelectionGate
import com.iflowmonitor.iflowlab.manifest.ManifestException
import com.iflowmonitor.iflowlab.manifest.ManifestParser
import com.iflowmonitor.iflowlab.manifest.TestCase
import com.iflowmonitor.iflowlab.model.RoutingMode
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives a suite: parse manifest, run each case on Saxon, apply the mode's gates, report, and return
 * a CI-suitable exit code (AC2, AC26, AC27).
 */
class RoutingRunner(private val out: Appendable = System.out) {

    /** @return 0 = all cases pass; 1 = at least one case fails; 2 = manifest/config error. */
    fun run(manifestPath: Path): Int {
        val manifest = try {
            ManifestParser.parse(manifestPath)
        } catch (e: ManifestException) {
            out.appendLine("ERROR: ${e.message}")
            return EXIT_CONFIG
        }

        var anyFail = false
        for (tc in manifest.tests) {
            val mode = tc.mode ?: manifest.mode
            if (mode == null) {
                out.appendLine("ERROR  ${tc.name}: mode required (declare 'mode' at suite or test level)")
                anyFail = true
                continue
            }
            val xslt = tc.xslt ?: manifest.xslt
            if (xslt == null) {
                out.appendLine("ERROR  ${tc.name}: no 'xslt' bound (declare at suite or test level)")
                anyFail = true
                continue
            }

            val results = try {
                val xsltFile = manifest.baseDir.resolve(xslt).normalize().toFile()
                val emitted = SaxonRunner.run(xsltFile, tc.params, resolveBody(tc, manifest.baseDir))
                val ctx = GateContext(
                    caseName = tc.name,
                    mode = mode,
                    emitted = emitted.doc,
                    expectation = tc.expectation,
                    namespaces = manifest.namespaces + tc.namespaces,
                )
                gatesFor(mode).map { it.evaluate(ctx) }
            } catch (e: Exception) {
                out.appendLine("FAIL  ${tc.name}  [stylesheet: $xslt]")
                out.appendLine("      engine error: ${e.message}")
                anyFail = true
                continue
            }

            val passed = results.none { it.failed }
            if (!passed) anyFail = true
            printCase(tc.name, xslt, passed, results)
        }
        return if (anyFail) EXIT_FAIL else EXIT_OK
    }

    private fun resolveBody(tc: TestCase, baseDir: Path): String = when {
        tc.body != null -> tc.body
        tc.bodyFile != null -> Files.readString(baseDir.resolve(tc.bodyFile).normalize())
        else -> DUMMY_BODY
    }

    private fun printCase(name: String, xslt: String, passed: Boolean, results: List<GateResult>) {
        out.appendLine("${if (passed) "PASS" else "FAIL"}  $name  [stylesheet: $xslt]")
        for (r in results) {
            if (r.outcome != GateOutcome.PASS) {
                out.appendLine("      ${r.outcome} ${r.gateName}: ${r.message}")
                r.findings.forEach { out.appendLine("        - $it") }
            }
            r.warnings.forEach { out.appendLine("      ! ${r.gateName}: $it") }
        }
    }

    companion object {
        const val EXIT_OK = 0
        const val EXIT_FAIL = 1
        const val EXIT_CONFIG = 2
    }
}

/**
 * The mode-driven gate pipeline. P1: selection only. Later phases widen the per-mode set (XSD gate
 * P3, shape-consistency P6, interface-pending P8) without touching the runner loop.
 */
fun gatesFor(mode: RoutingMode): List<Gate> = when (mode) {
    RoutingMode.RECEIVER -> listOf(SelectionGate())
    RoutingMode.COMBINED -> listOf(SelectionGate())
    RoutingMode.INTERFACE -> listOf(SelectionGate())
}
