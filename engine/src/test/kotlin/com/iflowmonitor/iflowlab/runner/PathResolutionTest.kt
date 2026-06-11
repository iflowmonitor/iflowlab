package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * AC27 — relative `xslt`/`bodyFile` paths resolve against the MANIFEST file's directory, not the
 * process CWD, so a suite produces identical results from any working directory.
 */
class PathResolutionTest {

    private fun run(manifest: Path): SuiteResult = RoutingRunner().run(manifest)

    private fun seed(dir: Path, fixtureName: String, asName: String) {
        Files.copy(fixture(fixtureName).toPath(), dir.resolve(asName))
    }

    /** `bodyFile:` resolves against the manifest dir — the payload exists ONLY there, never in CWD. */
    @Test
    fun bodyFileResolvesAgainstManifestDir(@TempDir dir: Path) {
        seed(dir, "body-echo.xslt", "r.xslt")
        Files.writeString(dir.resolve("payload.xml"), "<Order/>")
        val m = Files.writeString(
            dir.resolve("suite.yaml"),
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: routes by body root
                bodyFile: payload.xml
                expect:
                  receivers:
                    - name: Order
            """.trimIndent(),
        )
        val result = run(m)
        assertTrue(result.cases.single().passed, "$result")
    }

    /** Inline `body:` is fed to the stylesheet verbatim. */
    @Test
    fun inlineBodyIsUsed(@TempDir dir: Path) {
        seed(dir, "body-echo.xslt", "r.xslt")
        val m = Files.writeString(
            dir.resolve("suite.yaml"),
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: inline body
                body: "<Invoice/>"
                expect:
                  receivers:
                    - name: Invoice
            """.trimIndent(),
        )
        val result = run(m)
        assertTrue(result.cases.single().passed, "$result")
    }

    /**
     * CWD-independence: two manifests in two different dirs each reference the SAME relative name
     * `r.xslt` but a DIFFERENT stylesheet. Each must resolve its own dir's copy. The process CWD
     * contains no `r.xslt`, so any CWD-relative resolution would fail to find it entirely.
     */
    @Test
    fun resolvesPerManifestDirNotCwd(@TempDir root: Path) {
        val dirA = Files.createDirectory(root.resolve("a"))
        val dirB = Files.createDirectory(root.resolve("b"))
        // dirA's r.xslt always emits BANK_A (ONE); dirB's always emits both (BOTH) via dc_Route.
        seed(dirA, "receiver-route.xslt", "r.xslt")
        seed(dirB, "receiver-route.xslt", "r.xslt")
        val a = Files.writeString(
            dirA.resolve("suite.yaml"),
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: A
                params: { dc_Route: ONE }
                expect: { receivers: [ { name: BANK_A } ] }
            """.trimIndent(),
        )
        val b = Files.writeString(
            dirB.resolve("suite.yaml"),
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: B
                params: { dc_Route: BOTH }
                expect: { receivers: [ { name: BANK_A }, { name: BANK_B } ] }
            """.trimIndent(),
        )
        val resultA = run(a)
        val resultB = run(b)
        assertTrue(resultA.cases.single().passed, "$resultA")
        assertTrue(resultB.cases.single().passed, "$resultB")
        // Both resolved their own dir's stylesheet even though neither file sits in the process CWD.
        assertTrue(resultA.cases.single().xslt == "r.xslt" && resultB.cases.single().xslt == "r.xslt")
    }
}
