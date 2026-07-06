package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** P8 — interface-only mode: flat interface matching + the O9 XSD-pending warning (AC25). */
class InterfaceModeTest {

    @TempDir
    lateinit var dir: Path

    private fun run(manifestBody: String): Pair<Int, String> {
        Files.copy(fixture("interface-route.xslt").toPath(), dir.resolve("r.xslt"))
        val m = Files.writeString(dir.resolve("suite.yaml"), "xslt: r.xslt\nmode: interface\n$manifestBody")
        val sb = StringBuilder()
        return RoutingRunner(sb).run(m) to sb.toString()
    }

    /** AC25 — interface-mode run applies the assertion gate AND emits the literal "XSD gate pending (O9)" warning. */
    @Test
    fun interfaceModeAppliesAssertionGateAndWarnsXsdPending() {
        val (code, output) = run(
            """
            tests:
              - name: flat interfaces
                params: { dc_Index: "yes" }
                expect:
                  interfaces:
                    - { endpoint: /pip/ep/i1, index: "1", name: IF_1 }
            """.trimIndent(),
        )
        assertEquals(0, code, output)
        assertTrue(output.contains("XSD gate pending (O9)"), "O9 warning must be emitted, never silently skipped: $output")
    }

    /** The assertion gate genuinely runs: a wrong endpoint fails the interface-mode case. */
    @Test
    fun interfaceSelectionMismatchFails() {
        val (code, output) = run(
            """
            tests:
              - name: wrong endpoint
                params: { dc_Index: "yes" }
                expect:
                  interfaces:
                    - { endpoint: /pip/ep/WRONG, index: "1", name: IF_1 }
            """.trimIndent(),
        )
        assertEquals(1, code)
        // The warning is still emitted even on a failing case (gate never silently skipped).
        assertTrue(output.contains("XSD gate pending (O9)"), output)
    }

    /** Interface identity is endpoint-anchored: omitting index matches an Interface that omits Index. */
    @Test
    fun interfaceWithoutIndexMatchesOmittedIndex() {
        val (code, output) = run(
            """
            tests:
              - name: no index
                params: { dc_Index: "no" }
                expect:
                  interfaces:
                    - { endpoint: /pip/ep/i1, name: IF_1 }
            """.trimIndent(),
        )
        assertEquals(0, code, output)
    }
}
