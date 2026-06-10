package com.iflowmonitor.iflowlab.model

/**
 * The determination mode of a routing stylesheet. Explicit, never inferred (PRD D10).
 * Drives root document, XSD gate selection, assertion shape, and the shape-consistency check.
 */
enum class RoutingMode { RECEIVER, COMBINED, INTERFACE }
