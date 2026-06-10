package com.iflowmonitor.iflowlab.model

/**
 * Namespace resolution for a case (PRD D3, AC6/AC7/AC15).
 *
 * `ns0 -> http://sap.com/xi/XI/System` is pre-registered by default and overridable. The effective
 * map merges, in increasing precedence: the ns0 default, the suite-level `namespaces:`, then the
 * per-case `namespaces:` (per-case wins for the same prefix).
 */
object Namespaces {
    const val NS0 = "ns0"
    const val SAP_SYSTEM = "http://sap.com/xi/XI/System"

    fun effective(suite: Map<String, String>, perCase: Map<String, String>): Map<String, String> =
        buildMap {
            put(NS0, SAP_SYSTEM) // pre-registered default
            putAll(suite)        // suite-level overrides the default
            putAll(perCase)      // per-case overrides suite-level
        }
}
