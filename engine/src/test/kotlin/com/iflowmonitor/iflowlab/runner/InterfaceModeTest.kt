package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** P8 — interface-only mode: flat interface matching + the O9 XSD-pending warning (AC25). */
class InterfaceModeTest {

    @TempDir
    lateinit var dir: Path

    private fun run(manifestBody: String): CaseResult {
        Files.copy(fixture("interface-route.xslt").toPath(), dir.resolve("r.xslt"))
        val m = Files.writeString(dir.resolve("suite.yaml"), "xslt: r.xslt\nmode: interface\n$manifestBody")
        return RoutingRunner().run(m).cases.single()
    }

    private fun warningsOf(case: CaseResult): List<String> = case.gateResults.flatMap { it.warnings }

    /** AC25 — interface-mode run applies the assertion gate AND carries the literal "XSD gate pending (O9)" warning. */
    @Test
    fun interfaceModeAppliesAssertionGateAndWarnsXsdPending() {
        val case = run(
            """
            tests:
              - name: flat interfaces
                params: { dc_Index: "yes" }
                expect:
                  interfaces:
                    - { endpoint: /pip/ep/i1, index: "1", name: IF_1 }
            """.trimIndent(),
        )
        assertTrue(case.passed, "$case")
        assertTrue(
            warningsOf(case).any { it.contains("XSD gate pending (O9)") },
            "O9 warning must be carried, never silently skipped: $case",
        )
    }

    /** The assertion gate genuinely runs: a wrong endpoint fails the interface-mode case. */
    @Test
    fun interfaceSelectionMismatchFails() {
        val case = run(
            """
            tests:
              - name: wrong endpoint
                params: { dc_Index: "yes" }
                expect:
                  interfaces:
                    - { endpoint: /pip/ep/WRONG, index: "1", name: IF_1 }
            """.trimIndent(),
        )
        assertFalse(case.passed, "$case")
        // The warning is still carried even on a failing case (gate never silently skipped).
        assertTrue(warningsOf(case).any { it.contains("XSD gate pending (O9)") }, "$case")
    }

    /** Interface identity is endpoint-anchored: omitting index matches an Interface that omits Index. */
    @Test
    fun interfaceWithoutIndexMatchesOmittedIndex() {
        val case = run(
            """
            tests:
              - name: no index
                params: { dc_Index: "no" }
                expect:
                  interfaces:
                    - { endpoint: /pip/ep/i1, name: IF_1 }
            """.trimIndent(),
        )
        assertTrue(case.passed, "$case")
    }
}
