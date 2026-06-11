package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** P7 — header-only cases: dummy-body injection (AC8) and the explicit header-only flag (AC9). */
class HeaderOnlyTest {

    @TempDir
    lateinit var dir: Path

    /** AC8 + AC9 — a case with neither body nor bodyFile runs (no missing-input error) and is flagged. */
    @Test
    fun headerOnlyCaseRunsAndIsFlagged() {
        Files.copy(fixture("header-only.xslt").toPath(), dir.resolve("r.xslt"))
        val m = Files.writeString(
            dir.resolve("suite.yaml"),
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: header only routing
                params: { dc_Receiver: HEADER_RCV }
                expect:
                  receivers:
                    - name: HEADER_RCV
            """.trimIndent(),
        )
        val case = RoutingRunner().run(m).cases.single()

        assertTrue(case.passed, "$case")
        assertTrue(case.headerOnly, "case must be flagged header-only: $case")
    }

    /** A case WITH an inline body must NOT carry the header-only flag. */
    @Test
    fun bodyCaseIsNotFlaggedHeaderOnly() {
        Files.copy(fixture("header-only.xslt").toPath(), dir.resolve("r.xslt"))
        val m = Files.writeString(
            dir.resolve("suite.yaml"),
            """
            xslt: r.xslt
            mode: receiver
            tests:
              - name: has a body
                body: "<anything/>"
                params: { dc_Receiver: HEADER_RCV }
                expect:
                  receivers:
                    - name: HEADER_RCV
            """.trimIndent(),
        )
        val case = RoutingRunner().run(m).cases.single()
        assertFalse(case.headerOnly, "$case")
    }
}
