package com.iflowmonitor.iflowlab.manifest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ManifestParserTest {

    @TempDir
    lateinit var dir: Path

    private fun write(name: String, content: String): Path =
        Files.writeString(dir.resolve(name), content)

    /** AC1 — a valid YAML manifest parses and yields its tests. */
    @Test
    fun parsesValidManifest() {
        val p = write(
            "suite.yaml",
            """
            xslt: ./r.xslt
            mode: receiver
            tests:
              - name: routes to A
                params: { dc_Route: ONE }
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        val m = ManifestParser.parse(p)
        assertEquals("./r.xslt", m.xslt)
        assertEquals(1, m.tests.size)
        assertEquals("routes to A", m.tests[0].name)
        assertEquals(listOf("BANK_A"), m.tests[0].expectation.receivers.map { it.name })
    }

    /** AC1 — non-YAML / non-mapping input is rejected with a ManifestException (→ non-zero exit). */
    @Test
    fun rejectsNonYaml() {
        val p = write("bad.yaml", "this: is: not: valid: yaml: {[}")
        assertThrows(ManifestException::class.java) { ManifestParser.parse(p) }
    }

    /** AC1 — a scalar/list root (not a mapping) is rejected. */
    @Test
    fun rejectsNonMappingRoot() {
        val p = write("list.yaml", "- just\n- a\n- list")
        val ex = assertThrows(ManifestException::class.java) { ManifestParser.parse(p) }
        assertTrue(ex.message!!.contains("mapping"), ex.message)
    }

    /** AC12 — a `Service` key at receiver level is rejected; only `name` is accepted. */
    @Test
    fun rejectsServiceKeyAtReceiverLevel() {
        val p = write(
            "svc.yaml",
            """
            xslt: ./r.xslt
            mode: receiver
            tests:
              - name: t
                expect:
                  receivers:
                    - Service: BANK_A
            """.trimIndent(),
        )
        val ex = assertThrows(ManifestException::class.java) { ManifestParser.parse(p) }
        assertTrue(ex.message!!.contains("name"), ex.message)
    }
}
