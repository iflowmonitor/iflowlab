package com.iflowmonitor.iflowlab.app.lint;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.app.lint.Finding.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fidelity lint over CPI Groovy source (slice 7). */
class FidelityLinterTest {

    private final FidelityLinter linter = new FidelityLinter();

    @Test
    void mockedServices_produceNoFindings() {
        String src =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "import com.sap.it.api.ITApiFactory\n"
                        + "import com.sap.it.api.mapping.ValueMappingApi\n"
                        + "import com.sap.it.api.securestore.SecureStoreService\n"
                        + "Message processData(Message message) { return message }\n";
        assertThat(linter.lint(src)).isEmpty();
    }

    @Test
    void unmockedService_isFlaggedOnTheImportLine() {
        String src =
                "import com.sap.it.api.asdk.datastore.DataStoreService\n"
                        + "Message processData(Message message) { return message }\n";
        List<Finding> findings = linter.lint(src);
        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.line()).isEqualTo(1);
        assertThat(f.rule()).isEqualTo("unmocked-service");
        assertThat(f.severity()).isEqualTo(Severity.WARNING);
        assertThat(f.message()).contains("DataStoreService").contains("not mocked");
        assertThat(f.column()).isGreaterThan(0);
        assertThat(f.endColumn()).isGreaterThan(f.column());
    }

    @Test
    void filesystemAndNetworkAccess_areFlagged() {
        String src =
                "def f = new File('/etc/passwd')\n"
                        + "def c = new URL('http://x').openConnection()\n";
        List<Finding> findings = linter.lint(src);
        assertThat(findings).extracting(Finding::rule).containsOnly("sandbox-escape");
        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).line()).isEqualTo(1);
        assertThat(findings.get(1).line()).isEqualTo(2);
    }

    @Test
    void commentedOutCode_isNotFlagged() {
        String src = "// import com.sap.it.api.asdk.datastore.DataStoreService\n"
                + "def x = 1 // new File('nope')\n";
        assertThat(linter.lint(src)).isEmpty();
    }

    @Test
    void blankOrNullSource_isClean() {
        assertThat(linter.lint("")).isEmpty();
        assertThat(linter.lint(null)).isEmpty();
        assertThat(linter.lint("   \n  \n")).isEmpty();
    }

    @Test
    void systemExit_isFlagged() {
        assertThat(linter.lint("System.exit(1)\n"))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.rule()).isEqualTo("sandbox-escape");
                    assertThat(f.message()).contains("System.exit");
                });
    }
}
