package com.iflowmonitor.iflowlab.engine.assertions;

/** The verdict on one {@link Assertion}: whether it held, and the actual value observed. */
public record AssertionResult(Assertion assertion, boolean passed, String actual) {}
