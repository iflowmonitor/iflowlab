package com.iflowmonitor.iflowlab.model

/**
 * What a test case expects the stylesheet to emit. Grows per phase; the [com.iflowmonitor.iflowlab.gate.Gate]
 * seam never changes when this widens.
 *
 * P1: receiver-name set only. Later: party, nested interfaces (P4), notDetermined (P5),
 * flat interfaces for interface mode (P8).
 */
data class Expectation(
    val receivers: List<ReceiverSpec>,
)

/** A receiver assertion tuple. Identity = [name] (= `Receiver/Service`), or (party + name) when party present. */
data class ReceiverSpec(
    val name: String,
    val party: PartySpec? = null,
    /** null = interfaces not asserted (P4+); empty = assert zero interfaces. */
    val interfaces: List<InterfaceSpec>? = null,
)

/** `Receiver/Party` (+ `@agency`, `@scheme`). Optional (P5). */
data class PartySpec(val value: String, val agency: String? = null, val scheme: String? = null)

/** An interface assertion tuple. Identity anchored on [endpoint] (= `Interface/Service`), never on index (P4). */
data class InterfaceSpec(val endpoint: String, val index: String? = null, val name: String? = null)
