package com.iflowmonitor.iflowlab.cli

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * AC11 — the post-reshape pipeline (engine `run` + :cli renderer) reproduces the pre-reshape CLI
 * stdout byte-for-byte. The golden was captured from the pre-split CLI on examples/receiver-suite.yaml
 * (319 bytes, LF-only).
 */
class GoldenStdoutTest {

    @Test
    fun pipelineStdoutIsByteIdenticalToPreReshapeGolden() {
        val manifest = repoRoot().resolve("examples/receiver-suite.yaml")
        val out = StringBuilder()
        val code = runCli(arrayOf(manifest.toString()), out, StringBuilder())

        val golden = javaClass.getResourceAsStream("/golden/receiver-suite.stdout.txt")!!.use { it.readBytes() }
        assertArrayEquals(golden, out.toString().toByteArray(Charsets.UTF_8), "stdout must be byte-identical to the golden")
        assertEquals(EXIT_OK, code)
    }

    /** The test JVM's working dir is the module dir — walk up to the repo root (settings.gradle.kts). */
    private fun repoRoot(): Path {
        var dir = Path.of("").toAbsolutePath()
        while (!Files.exists(dir.resolve("settings.gradle.kts"))) {
            dir = dir.parent ?: error("settings.gradle.kts not found above ${Path.of("").toAbsolutePath()}")
        }
        return dir
    }
}
