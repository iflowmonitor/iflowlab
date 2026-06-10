package com.iflowmonitor.iflowlab.runner

import com.iflowmonitor.iflowlab.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** P5 — ReceiverNotDetermined (type + defaultReceiver), party identity, zero-receivers (AC21/22/23). */
class NotDeterminedAndPartyTest {

    @TempDir
    lateinit var dir: Path

    private fun run(fixtureName: String, manifestBody: String): Pair<Int, String> {
        Files.copy(fixture(fixtureName).toPath(), dir.resolve("r.xslt"))
        val m = Files.writeString(dir.resolve("suite.yaml"), "xslt: r.xslt\nmode: receiver\n$manifestBody")
        val sb = StringBuilder()
        return RoutingRunner(sb).run(m) to sb.toString()
    }

    /** AC21 — a Type value outside {Error,Ignore,Default} that matches the emitted string passes (no enum check). */
    @Test
    fun freeFormNotDeterminedTypePasses() {
        val (code, output) = run(
            "notdetermined-route.xslt",
            """
            tests:
              - name: free-form type
                params: { dc_Type: Wibble }
                expect:
                  receivers: []
                  notDetermined:
                    type: Wibble
            """.trimIndent(),
        )
        assertEquals(0, code, output)
    }

    /** AC21 — a Type that does NOT match the emitted string fails (exact compare). */
    @Test
    fun mismatchedNotDeterminedTypeFails() {
        val (code, _) = run(
            "notdetermined-route.xslt",
            """
            tests:
              - name: wrong type
                params: { dc_Type: Error }
                expect:
                  receivers: []
                  notDetermined:
                    type: Ignore
            """.trimIndent(),
        )
        assertEquals(1, code)
    }

    /** AC22 — defaultReceiver matched as a full receiver tuple (here by name FALLBACK). */
    @Test
    fun defaultReceiverFullTupleMatches() {
        val (code, output) = run(
            "notdetermined-route.xslt",
            """
            tests:
              - name: default receiver
                params: { dc_Type: Error }
                expect:
                  receivers: []
                  notDetermined:
                    type: Error
                    defaultReceiver: { name: FALLBACK }
            """.trimIndent(),
        )
        assertEquals(0, code, output)
    }

    /** AC22 — a wrong defaultReceiver name fails. */
    @Test
    fun defaultReceiverWrongNameFails() {
        val (code, _) = run(
            "notdetermined-route.xslt",
            """
            tests:
              - name: wrong default
                params: { dc_Type: Error }
                expect:
                  receivers: []
                  notDetermined:
                    defaultReceiver: { name: NOT_FALLBACK }
            """.trimIndent(),
        )
        assertEquals(1, code)
    }

    /** AC23 — expecting zero receivers passes when none are emitted. */
    @Test
    fun zeroReceiversPassesWhenNoneEmitted() {
        val (code, output) = run(
            "notdetermined-route.xslt",
            """
            tests:
              - name: zero receivers
                params: { dc_Type: Error }
                expect:
                  receivers: []
            """.trimIndent(),
        )
        assertEquals(0, code, output)
    }

    /** AC23 — expecting zero receivers fails when a receiver IS emitted. */
    @Test
    fun zeroReceiversFailsWhenAnyEmitted() {
        val (code, _) = run(
            "receiver-route.xslt",
            """
            tests:
              - name: expected none but got one
                params: { dc_Route: ONE }
                expect:
                  receivers: []
            """.trimIndent(),
        )
        assertEquals(1, code)
    }

    /** Party is part of receiver identity: a full party tuple matches the emitted Party. */
    @Test
    fun partyTupleMatches() {
        val (code, output) = run(
            "party-route.xslt",
            """
            tests:
              - name: party match
                expect:
                  receivers:
                    - name: BANK_A
                      party: { value: PARTY_V, agency: A1, scheme: S1 }
            """.trimIndent(),
        )
        assertEquals(0, code, output)
    }

    /** A wrong party value fails. */
    @Test
    fun partyValueMismatchFails() {
        val (code, _) = run(
            "party-route.xslt",
            """
            tests:
              - name: party mismatch
                expect:
                  receivers:
                    - name: BANK_A
                      party: { value: WRONG, agency: A1, scheme: S1 }
            """.trimIndent(),
        )
        assertEquals(1, code)
    }
}
