package com.iflowmonitor.iflowlab.engine;

/**
 * The engine-agnostic run contract (D2). Groovy is the first implementation;
 * XSLT ports later and the slice-2 debugger implements the same shape. Kept
 * deliberately narrow so it is deep and rarely changes.
 */
public interface Engine {

    RunResult run(RunRequest request);
}
