package com.iflowmonitor.iflowlab.cli

import com.iflowmonitor.iflowlab.runner.SuiteResult

/** CI-suitable exit codes (AC9/AC10) — the mapping lives here, not in :engine. */
const val EXIT_OK = 0
const val EXIT_FAIL = 1
const val EXIT_CONFIG = 2

/** 0 = all cases pass; 1 = at least one case fails; 2 = manifest/config error. */
fun exitCodeFor(result: SuiteResult): Int = when {
    result.configError != null -> EXIT_CONFIG
    result.cases.any { !it.passed } -> EXIT_FAIL
    else -> EXIT_OK
}
