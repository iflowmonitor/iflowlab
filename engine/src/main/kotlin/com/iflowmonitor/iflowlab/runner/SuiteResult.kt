package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.gate.GateResult

/**
 * The typed outcome of a suite run (AC5/AC6). Errors are data, never exceptions out of `run`:
 * [configError] is a suite-level manifest/config failure — when set, [cases] is empty (AC7).
 */
data class SuiteResult(
    val cases: List<CaseResult>,
    val configError: String? = null,
)

/**
 * One case's outcome. [engineError] is a per-case failure (AC8): a binding error (no mode/xslt —
 * then [xslt] is null, since no stylesheet was executed) or a stylesheet exception ([xslt] names
 * the failing stylesheet). [gateResults] is empty whenever [engineError] is set.
 */
data class CaseResult(
    val name: String,
    val xslt: String?,
    val headerOnly: Boolean,
    val gateResults: List<GateResult>,
    val engineError: String? = null,
) {
    val passed: Boolean get() = engineError == null && gateResults.none { it.failed }
}
