package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RoutingRunnerTest {

    @TempDir
    lateinit var dir: Path

    /** Copy the routing fixture into [dir] so the only resolvable copy is beside the manifest (AC27). */
    private fun seedStylesheet() {
        Files.copy(fixture("receiver-route.xslt").toPath(), dir.resolve("r.xslt"))
    }

    private fun writeManifest(content: String): Path = Files.writeString(dir.resolve("suite.yaml"), content)

    private fun run(manifest: Path): Pair<Int, String> {
        val sb = StringBuilder()
        val code = RoutingRunner(sb).run(manifest)
        return code to sb.toString()
    }

    /** AC2 + AC26 (zero) + AC27 — relative xslt resolves against the manifest dir; all-pass exits 0. */
    @Test
    fun allPassExitsZeroAndNamesStylesheet() {
        seedStylesheet()
        val m = writeManifest(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: one receiver
                params: { dc_Route: ONE }
                expect:
                  receivers:
                    - name: BANK_A
              - name: both receivers (order-insensitive)
                params: { dc_Route: BOTH }
                expect:
                  receivers:
                    - name: BANK_A
                    - name: BANK_B
            """.trimIndent(),
        )
        val (code, output) = run(m)
        assertEquals(0, code, output)
        assertTrue(output.contains("[stylesheet: r.xslt]"), "runner output should name the stylesheet: $output")
    }

    /** AC26 — any failing case exits non-zero (1). */
    @Test
    fun anyFailureExitsNonZero() {
        seedStylesheet()
        val m = writeManifest(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: wrong expectation
                params: { dc_Route: ONE }
                expect:
                  receivers:
                    - name: BANK_A
                    - name: BANK_B
            """.trimIndent(),
        )
        val (code, output) = run(m)
        assertEquals(1, code, output)
        assertTrue(output.contains("FAIL"), output)
    }

    /** AC1 — a non-YAML manifest yields the config exit code (2). */
    @Test
    fun badManifestExitsConfigCode() {
        val m = writeManifest("this: is: not: valid: {[}")
        val (code, _) = run(m)
        assertEquals(2, code)
    }
}
