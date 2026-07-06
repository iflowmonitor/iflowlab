package com.iflowmonitor.iflowlab.cpimock.services;

/** One value-mapping row: the source coordinates and the target value they map to. */
public record ValueMappingEntry(
        String sourceAgency,
        String sourceIdentifier,
        String sourceValue,
        String targetAgency,
        String targetIdentifier,
        String value) {}
