package com.iflowmonitor.iflowlab.cli

import com.iflowmonitor.iflowlab.runner.RoutingRunner
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * CLI entrypoint. `iflowlab <manifest.yaml>` runs the suite and exits with the runner's code
 * (0 pass / 1 case failure / 2 config error) so routing regressions break CI (AC26).
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: iflowlab <manifest.yaml>")
        exitProcess(RoutingRunner.EXIT_CONFIG)
    }
    exitProcess(RoutingRunner().run(Path.of(args[0])))
}
