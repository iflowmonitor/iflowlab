package com.iflowmonitor.iflowlab.manifest

import com.iflowmonitor.iflowlab.model.Expectation
import com.iflowmonitor.iflowlab.model.InterfaceSpec
import com.iflowmonitor.iflowlab.model.ReceiverSpec
import com.iflowmonitor.iflowlab.model.RoutingMode
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parses a YAML test manifest into a [Manifest]. Untyped snakeyaml traversal (not typed binding) so
 * that every validation failure produces a precise, AC-observable message (AC1, AC3, AC12).
 */
object ManifestParser {

    fun parse(path: Path): Manifest {
        val text = try {
            Files.readString(path)
        } catch (e: Exception) {
            throw ManifestException("cannot read manifest '$path': ${e.message}")
        }

        val raw: Any? = try {
            // Explicit type arg: inside a throwing try/catch, Kotlin otherwise infers T=Nothing (→ Void)
            // and snakeyaml's generic load() inserts a bogus checkcast to Void.
            Yaml().load<Any?>(text)
        } catch (e: YAMLException) {
            throw ManifestException("manifest is not valid YAML: ${e.message}")
        }
        val root = raw as? Map<*, *>
            ?: throw ManifestException(
                "manifest root must be a YAML mapping (got ${raw?.javaClass?.simpleName ?: "empty document"})",
            )

        val tests = (root["tests"] as? List<*>
            ?: throw ManifestException("manifest must declare a 'tests:' list"))
            .mapIndexed { i, t ->
                val m = t as? Map<*, *> ?: throw ManifestException("tests[$i] must be a mapping")
                parseTest(m, i)
            }

        return Manifest(
            baseDir = path.toAbsolutePath().normalize().parent,
            xslt = root["xslt"]?.toString(),
            mode = parseMode(root["mode"]),
            namespaces = parseNamespaces(root["namespaces"]),
            tests = tests,
        )
    }

    private fun parseMode(v: Any?): RoutingMode? = when (val s = v?.toString()?.lowercase()) {
        null -> null
        "receiver" -> RoutingMode.RECEIVER
        "combined" -> RoutingMode.COMBINED
        "interface" -> RoutingMode.INTERFACE
        else -> throw ManifestException("unknown mode '$s' (expected receiver | combined | interface)")
    }

    private fun parseNamespaces(v: Any?): Map<String, String> {
        if (v == null) return emptyMap()
        val m = v as? Map<*, *>
            ?: throw ManifestException("'namespaces' must be a mapping of prefix -> URI")
        return m.entries.associate { (k, u) -> k.toString() to u.toString() }
    }

    private fun parseParams(v: Any?): Map<String, String> {
        if (v == null) return emptyMap()
        val m = v as? Map<*, *> ?: throw ManifestException("'params' must be a mapping")
        // dc_ values coerced to string to match the CPI runtime boundary (AC5; P2 covers int/bool fidelity).
        return m.entries.associate { (k, x) -> k.toString() to x.toString() }
    }

    private fun parseTest(m: Map<*, *>, i: Int): TestCase {
        val name = m["name"]?.toString() ?: throw ManifestException("tests[$i] is missing 'name'")
        val expect = m["expect"] as? Map<*, *>
            ?: throw ManifestException("test '$name' is missing an 'expect:' block")
        return TestCase(
            name = name,
            xslt = m["xslt"]?.toString(),
            mode = parseMode(m["mode"]),
            params = parseParams(m["params"]),
            namespaces = parseNamespaces(m["namespaces"]),
            body = m["body"]?.toString(),
            bodyFile = m["bodyFile"]?.toString(),
            expectation = parseExpectation(expect, name),
        )
    }

    private val RECEIVER_KEYS = setOf("name", "party", "interfaces")

    private fun parseExpectation(expect: Map<*, *>, caseName: String): Expectation {
        val recvRaw = expect["receivers"] as? List<*> ?: emptyList<Any?>()
        val receivers = recvRaw.mapIndexed { i, r ->
            val rm = r as? Map<*, *>
                ?: throw ManifestException("test '$caseName' receivers[$i] must be a mapping")
            val unknown = rm.keys.map { it.toString() } - RECEIVER_KEYS
            if (unknown.isNotEmpty()) {
                throw ManifestException(
                    "test '$caseName' receivers[$i] has unsupported key(s) $unknown; " +
                        "use 'name' (maps to Receiver/Service) — 'Service' is not a manifest key",
                )
            }
            val nm = rm["name"]?.toString()
                ?: throw ManifestException("test '$caseName' receivers[$i] is missing 'name'")
            ReceiverSpec(name = nm, interfaces = parseInterfaces(rm["interfaces"], caseName, i))
        }
        return Expectation(receivers = receivers)
    }

    private val INTERFACE_KEYS = setOf("endpoint", "index", "name")

    /** Nested combined-mode interfaces. null = not asserted; a list (possibly empty) = asserted (P4). */
    private fun parseInterfaces(v: Any?, caseName: String, ri: Int): List<InterfaceSpec>? {
        if (v == null) return null
        val list = v as? List<*>
            ?: throw ManifestException("test '$caseName' receivers[$ri] 'interfaces' must be a list")
        return list.mapIndexed { j, iface ->
            val im = iface as? Map<*, *>
                ?: throw ManifestException("test '$caseName' receivers[$ri] interfaces[$j] must be a mapping")
            val unknown = im.keys.map { it.toString() } - INTERFACE_KEYS
            if (unknown.isNotEmpty()) {
                throw ManifestException(
                    "test '$caseName' receivers[$ri] interfaces[$j] has unsupported key(s) $unknown; " +
                        "use 'endpoint' (maps to Interface/Service), 'index', 'name'",
                )
            }
            val endpoint = im["endpoint"]?.toString()
                ?: throw ManifestException(
                    "test '$caseName' receivers[$ri] interfaces[$j] is missing 'endpoint'",
                )
            // index is an asserted string VALUE when present (never coerced/required) — PRD §D7.
            InterfaceSpec(endpoint = endpoint, index = im["index"]?.toString(), name = im["name"]?.toString())
        }
    }
}
