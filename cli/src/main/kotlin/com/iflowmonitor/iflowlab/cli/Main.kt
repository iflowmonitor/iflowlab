package com.iflowmonitor.iflowlab.cli

import com.iflowmonitor.iflowlab.runner.RoutingRunner
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * CLI entrypoint. `iflowlab <manifest.yaml>` runs the suite, renders the report, and exits with
 * 0 pass / 1 case failure / 2 config error so routing regressions break CI (AC26, AC10).
 */
fun main(args: Array<String>) {
    val code = runCli(args, System.out, System.err)
    System.out.flush()
    exitProcess(code)
}

/** Testable core: run + render + map to an exit code, without touching the process (AC10/AC11). */
fun runCli(args: Array<String>, out: Appendable, err: Appendable): Int {
    if (args.isEmpty()) {
        err.appendLine("usage: iflowlab <manifest.yaml>")
        return EXIT_CONFIG
    }
    val result = RoutingRunner().run(Path.of(args[0]))
    out.append(renderSuite(result))
    return exitCodeFor(result)
}
