package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.engine.DUMMY_BODY
import com.iflowmonitor.iflowlab.engine.SaxonRunner
import com.iflowmonitor.iflowlab.gate.Gate
import com.iflowmonitor.iflowlab.gate.GateContext
import com.iflowmonitor.iflowlab.gate.InterfaceSelectionGate
import com.iflowmonitor.iflowlab.gate.InterfaceXsdPendingGate
import com.iflowmonitor.iflowlab.gate.SelectionGate
import com.iflowmonitor.iflowlab.gate.ShapeConsistencyGate
import com.iflowmonitor.iflowlab.gate.XsdGate
import com.iflowmonitor.iflowlab.manifest.Manifest
import com.iflowmonitor.iflowlab.manifest.ManifestException
import com.iflowmonitor.iflowlab.manifest.ManifestParser
import com.iflowmonitor.iflowlab.manifest.TestCase
import com.iflowmonitor.iflowlab.model.Namespaces
import com.iflowmonitor.iflowlab.model.RoutingMode
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives a suite: parse manifest, run each case on Saxon, apply the mode's gates, and return the
 * typed [SuiteResult] (AC5/AC6). Pure: no printing, no exit codes, and no engine/config exception
 * ever escapes [run] — errors surface as data ([SuiteResult.configError], [CaseResult.engineError]).
 * Rendering and the exit-code mapping live in :cli.
 */
class RoutingRunner {

    fun run(manifestPath: Path): SuiteResult {
        val manifest = try {
            ManifestParser.parse(manifestPath)
        } catch (e: ManifestException) {
            return SuiteResult(cases = emptyList(), configError = e.message)
        }
        return SuiteResult(cases = manifest.tests.map { runCase(it, manifest) })
    }

    private fun runCase(tc: TestCase, manifest: Manifest): CaseResult {
        val headerOnly = isHeaderOnly(tc)
        val mode = tc.mode ?: manifest.mode
            ?: return bindingError(tc.name, headerOnly, "mode required (declare 'mode' at suite or test level)")
        val xslt = tc.xslt ?: manifest.xslt
            ?: return bindingError(tc.name, headerOnly, "no 'xslt' bound (declare at suite or test level)")

        val results = try {
            val xsltFile = manifest.baseDir.resolve(xslt).normalize().toFile()
            val emitted = SaxonRunner.run(xsltFile, tc.params, resolveBody(tc, manifest.baseDir))
            val ctx = GateContext(
                caseName = tc.name,
                mode = mode,
                emitted = emitted.doc,
                expectation = tc.expectation,
                namespaces = Namespaces.effective(manifest.namespaces, tc.namespaces),
            )
            gatesFor(mode).map { it.evaluate(ctx) }
        } catch (e: Exception) {
            // e.message can be null (e.g. a bare NPE); coerce as string interpolation used to,
            // so the rendered "engine error: null" line stays byte-identical to the pre-reshape CLI.
            return CaseResult(tc.name, xslt, headerOnly, gateResults = emptyList(), engineError = e.message ?: "null")
        }
        return CaseResult(tc.name, xslt, headerOnly, gateResults = results)
    }

    /** No stylesheet was executed for this case — [CaseResult.xslt] is null by contract. */
    private fun bindingError(name: String, headerOnly: Boolean, message: String): CaseResult =
        CaseResult(name, xslt = null, headerOnly = headerOnly, gateResults = emptyList(), engineError = message)

    /** A case declaring neither `body:` nor `bodyFile:` is header-only — the dummy body is injected (AC8). */
    private fun isHeaderOnly(tc: TestCase): Boolean = tc.body == null && tc.bodyFile == null

    private fun resolveBody(tc: TestCase, baseDir: Path): String = when {
        tc.body != null -> tc.body
        tc.bodyFile != null -> Files.readString(baseDir.resolve(tc.bodyFile).normalize())
        else -> DUMMY_BODY
    }
}

/**
 * The mode-driven gate pipeline. P1: selection only. Later phases widen the per-mode set (XSD gate
 * P3, shape-consistency P6, interface-pending P8) without touching the runner loop.
 */
fun gatesFor(mode: RoutingMode): List<Gate> = when (mode) {
    // receiver: XSD (gate 1) + shape-consistency (AC24, receiver-only effect) + selection (gate 2).
    RoutingMode.RECEIVER -> listOf(XsdGate(), ShapeConsistencyGate(), SelectionGate())
    // combined: nested interfaces are expected, so the shape gate has no effect here — omit it.
    RoutingMode.COMBINED -> listOf(XsdGate(), SelectionGate())
    // interface mode: Interfaces XSD unsourced (O9) → pending-warning gate + flat interface selection (AC25).
    RoutingMode.INTERFACE -> listOf(InterfaceXsdPendingGate(), InterfaceSelectionGate())
}
