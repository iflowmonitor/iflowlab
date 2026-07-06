package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** P4 — combined mode: nested interface set-matching, endpoint-anchored, index present/absent (AC16/18/19). */
class CombinedModeTest {

    @TempDir
    lateinit var dir: Path

    private fun run(index: String, expectInterfaces: String): Pair<Int, String> {
        Files.copy(fixture("combined-route.xslt").toPath(), dir.resolve("r.xslt"))
        val m = Files.writeString(
            dir.resolve("suite.yaml"),
            """
            xslt: r.xslt
            mode: combined
            tests:
              - name: combined
                params: { dc_Index: "$index" }   # quoted: YAML 1.1 would read bare yes/no as a boolean
                expect:
                  receivers:
                    - name: BANK_A
                      interfaces:
            $expectInterfaces
            """.trimIndent(),
        )
        val sb = StringBuilder()
        return RoutingRunner(sb).run(m) to sb.toString()
    }

    /** AC16 — receiver with a nested interface (endpoint + index) matches the emitted tuple. */
    @Test
    fun nestedInterfaceWithIndexMatches() {
        val (code, output) = run("yes", "          - { endpoint: /pip/ep/a1, index: \"1\" }")
        assertEquals(0, code, output)
    }

    /** AC18 — expected endpoint with NO index matches an emitted Interface that omits Index. */
    @Test
    fun interfaceWithoutIndexMatchesOmittedIndex() {
        val (code, output) = run("no", "          - { endpoint: /pip/ep/a1 }")
        assertEquals(0, code, output)
    }

    /** AC19 — expected index but actual omits Index → fail. */
    @Test
    fun expectedIndexButActualOmitsFails() {
        val (code, _) = run("no", "          - { endpoint: /pip/ep/a1, index: \"1\" }")
        assertEquals(1, code)
    }

    /** AC19 (vice versa) — actual emits Index but expected omits → fail. */
    @Test
    fun actualIndexButExpectedOmitsFails() {
        val (code, _) = run("yes", "          - { endpoint: /pip/ep/a1 }")
        assertEquals(1, code)
    }

    /** A wrong endpoint fails (endpoint is the interface identity). */
    @Test
    fun wrongEndpointFails() {
        val (code, _) = run("yes", "          - { endpoint: /pip/ep/WRONG, index: \"1\" }")
        assertEquals(1, code)
    }
}
