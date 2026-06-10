package com.iflowmonitor.iflowlab.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Fidelity drift-guard for the canonical CPI header-only dummy body (PRD §D5, AC10/AC11).
 * Pins the exact bytes so any future change to the constant is caught.
 */
class DummyBodyTest {

    /** AC10 — the injected dummy equals the exact bytes `<dummy></dummy>`. */
    @Test
    fun dummyBodyIsExactBytes() {
        assertEquals("<dummy></dummy>", DUMMY_BODY)
    }

    /**
     * AC11 — the guard FAILS if the constant drifts: not the self-closing form, no XML prolog,
     * expanded form, no namespace.
     */
    @Test
    fun dummyBodyHasNoDriftedForms() {
        assertNotEquals("<dummy/>", DUMMY_BODY, "must be expanded form, not self-closing")
        assertFalse(DUMMY_BODY.contains("<?xml"), "must have no XML prolog")
        assertFalse(DUMMY_BODY.contains(":"), "must have no namespace prefix")
        assertEquals(15, DUMMY_BODY.length, "exact byte length of <dummy></dummy>")
    }
}
