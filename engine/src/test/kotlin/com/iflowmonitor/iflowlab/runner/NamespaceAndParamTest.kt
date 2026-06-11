package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** P2 — `dc_` value string-coercion (AC5) and namespace resolution (AC6/AC7/AC15). */
class NamespaceAndParamTest {

    @TempDir
    lateinit var dir: Path

    private fun seed(fixtureName: String, asName: String) {
        Files.copy(fixture(fixtureName).toPath(), dir.resolve(asName))
    }

    private fun run(content: String): CaseResult {
        val m = Files.writeString(dir.resolve("suite.yaml"), content)
        return RoutingRunner().run(m).cases.single()
    }

    /** AC5 — a YAML int param (`dc_Priority: 1`) reaches Saxon as the string "1". */
    @Test
    fun yamlIntParamCoercedToString() {
        seed("param-echo.xslt", "r.xslt")
        val case = run(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: int priority echoes as string
                params: { dc_Priority: 1 }
                expect:
                  receivers:
                    - name: "1"
            """.trimIndent(),
        )
        assertTrue(case.passed, "$case")
    }

    /** AC5 — a YAML bool param (`dc_Priority: true`) reaches Saxon as the string "true". */
    @Test
    fun yamlBoolParamCoercedToString() {
        seed("param-echo.xslt", "r.xslt")
        val case = run(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: bool param echoes as string
                params: { dc_Priority: true }
                expect:
                  receivers:
                    - name: "true"
            """.trimIndent(),
        )
        assertTrue(case.passed, "$case")
    }

    /** AC7 — with no `namespaces:` declared, ns0 is pre-registered to the SAP URI and resolves. */
    @Test
    fun ns0PreRegisteredWhenUndeclared() {
        seed("receiver-route.xslt", "r.xslt")
        val case = run(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: default ns0 resolves
                params: { dc_Route: ONE }
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        assertTrue(case.passed, "$case")
    }

    /** AC6 — a per-case `namespaces` entry overrides ns0; observable via the custom-namespace root. */
    @Test
    fun perCaseNs0OverrideApplies() {
        seed("receiver-custom-ns.xslt", "r.xslt")
        val case = run(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: per-case override matches custom root ns
                namespaces: { ns0: "urn:custom:v1" }
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        assertTrue(case.passed, "$case")
    }

    /** AC6 control — without the override, the default ns0 (SAP) does NOT match the custom root → fail. */
    @Test
    fun withoutOverrideCustomRootFails() {
        seed("receiver-custom-ns.xslt", "r.xslt")
        val case = run(
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: default ns0 mismatches custom root
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        assertFalse(case.passed, "$case")
    }

    /** AC15 — a suite-level ns0 mapping, not overridden per-case, stays in effect for the case. */
    @Test
    fun suiteLevelNamespaceRemainsInEffect() {
        seed("receiver-custom-ns.xslt", "r.xslt")
        val case = run(
            """
            xslt: r.xslt
            mode: receiver
            namespaces: { ns0: "urn:custom:v1" }
            tests:
              - name: inherits suite-level ns0
                expect:
                  receivers:
                    - name: BANK_A
            """.trimIndent(),
        )
        assertTrue(case.passed, "$case")
    }
}
