package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun run(manifest: Path): SuiteResult = RoutingRunner().run(manifest)

    /** AC2 + AC26 (zero) + AC27 — relative xslt resolves against the manifest dir; all cases pass. */
    @Test
    fun allPassAndEachCaseNamesItsStylesheet() {
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
        val result = run(m)
        assertNull(result.configError)
        assertTrue(result.cases.all { it.passed }, "all cases must pass: $result")
        assertTrue(result.cases.all { it.xslt == "r.xslt" }, "each case should name the stylesheet: $result")
    }

    /** AC26 — any failing case makes the suite fail (a case with a failed gate is not passed). */
    @Test
    fun anyFailureMakesSuiteFail() {
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
        val result = run(m)
        assertTrue(result.cases.any { !it.passed }, "a failing case must surface: $result")
        val case = result.cases.single()
        assertNull(case.engineError, "this is a gate failure, not an engine error")
        assertTrue(case.gateResults.any { it.failed }, "a failed gate must be recorded: $result")
    }

    /** AC1 — a non-YAML manifest yields a configError with no cases. */
    @Test
    fun badManifestYieldsConfigError() {
        val m = writeManifest("this: is: not: valid: {[}")
        val result = run(m)
        assertNotNull(result.configError)
        assertTrue(result.cases.isEmpty())
    }
}
