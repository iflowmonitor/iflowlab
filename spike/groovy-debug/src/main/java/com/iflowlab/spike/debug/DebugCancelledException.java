package com.iflowlab.spike.debug;

/**
 * Thrown from an instrumentation hook when the session has been cancelled,
 * so a parked or looping script unwinds cleanly instead of leaking its thread.
 */
public class DebugCancelledException extends RuntimeException {
    public DebugCancelledException() {
        super("debug session cancelled");
    }
}
