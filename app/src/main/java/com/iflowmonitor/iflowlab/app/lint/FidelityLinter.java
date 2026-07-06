package com.iflowmonitor.iflowlab.app.lint;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static fidelity lint for CPI Groovy scripts (slice 7). Flags constructs that run
 * in the workbench but would <em>not</em> behave the same on a real tenant, so a user
 * importing a production script sees the fidelity boundary up front rather than at run:
 *
 * <ul>
 *   <li>imports of {@code com.sap.it.api.*} services the workbench does not mock —
 *       those calls resolve to {@code null} here;</li>
 *   <li>filesystem / network / process access — sandboxed here and blocked on a tenant.</li>
 * </ul>
 *
 * A pure line scanner: source text in, findings out. No parsing, so it never fails on
 * a script that does not compile.
 */
public final class FidelityLinter {

    /** The CPI service APIs the workbench actually mocks (com.sap.it.api.*). */
    private static final Set<String> MOCKED_SERVICES = Set.of(
            "com.sap.it.api.ITApiFactory",
            "com.sap.it.api.mapping.ValueMappingApi",
            "com.sap.it.api.securestore.SecureStoreService",
            "com.sap.it.api.securestore.UserCredential",
            "com.sap.it.api.securestore.exception.SecureStoreException");

    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?(com\\.sap\\.it\\.api\\.[\\w.]+)");

    /** Sandbox-escape markers → the human reason, matched anywhere on a line. */
    private static final List<Rule> SANDBOX_RULES = List.of(
            new Rule(Pattern.compile("\\bnew\\s+File\\s*\\("), "Filesystem access is sandboxed in the workbench and blocked on a CPI tenant."),
            new Rule(Pattern.compile("\\bnew\\s+File(Input|Output)Stream\\s*\\("), "File I/O is sandboxed in the workbench and blocked on a CPI tenant."),
            new Rule(Pattern.compile("\\bjava\\.nio\\.file\\.Files\\b|\\bFiles\\.(read|write|copy|delete|move|newInputStream|newOutputStream)\\b"), "Filesystem access is sandboxed in the workbench and blocked on a CPI tenant."),
            new Rule(Pattern.compile("\\.openConnection\\s*\\(|\\bnew\\s+URL\\s*\\(|\\bHttpURLConnection\\b|\\bjava\\.net\\."), "Direct network access won't match a CPI tenant (use an adapter) and may be blocked in the workbench."),
            new Rule(Pattern.compile("\\bRuntime\\.getRuntime\\s*\\(\\)|\\bProcessBuilder\\b|\\.execute\\s*\\(\\s*\\)"), "Spawning processes is not supported in the workbench and blocked on a CPI tenant."),
            new Rule(Pattern.compile("\\bSystem\\.exit\\s*\\("), "System.exit terminates the workbench JVM; it never runs on a tenant."));

    public List<Finding> lint(String source) {
        List<Finding> findings = new ArrayList<>();
        if (source == null || source.isBlank()) {
            return findings;
        }
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = stripComment(raw);
            int lineNo = i + 1;

            Matcher im = IMPORT.matcher(line);
            if (im.find()) {
                String fqn = im.group(2);
                if (!MOCKED_SERVICES.contains(fqn)) {
                    int col = line.indexOf(fqn) + 1;
                    findings.add(new Finding(lineNo, col, col + fqn.length(),
                            Finding.Severity.WARNING, "unmocked-service",
                            "CPI service '" + fqn + "' is not mocked — calls will return null in the workbench, not run faithfully."));
                }
                continue; // an import line is not also a sandbox-escape
            }

            for (Rule rule : SANDBOX_RULES) {
                Matcher m = rule.pattern.matcher(line);
                if (m.find()) {
                    findings.add(new Finding(lineNo, m.start() + 1, m.end() + 1,
                            Finding.Severity.WARNING, "sandbox-escape", rule.message));
                    break; // one sandbox finding per line is enough
                }
            }
        }
        return findings;
    }

    /** Drop a trailing line comment so we don't lint commented-out code. */
    private static String stripComment(String line) {
        int idx = line.indexOf("//");
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    private record Rule(Pattern pattern, String message) {}
}
