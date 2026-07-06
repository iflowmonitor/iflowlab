package com.iflowmonitor.iflowlab.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflowmonitor.iflowlab.app.cases.MessageSpec;
import com.iflowmonitor.iflowlab.app.cases.RunCase;
import com.iflowmonitor.iflowlab.engine.assertions.Assertion;
import com.iflowmonitor.iflowlab.engine.assertions.Assertion.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace file access (R5), independent of Quarkus boot. */
class WorkspaceServiceTest {

    @Test
    void listsScripts_readsScript_andLoadsMessageFixture(@TempDir Path ws) throws Exception {
        Files.createDirectories(ws.resolve("scripts"));
        Files.writeString(ws.resolve("scripts/Upper.groovy"), "// upper");
        Path msg = ws.resolve("messages/sample");
        Files.createDirectories(msg);
        Files.writeString(msg.resolve("body.xml"), "<a/>");
        Files.writeString(msg.resolve("message.yaml"),
                "contentType: application/xml\nheaders:\n  H1: v1\nproperties:\n  P1: 2\n");

        WorkspaceService svc = new WorkspaceService(ws.toString());

        assertThat(svc.listScripts()).containsExactly("scripts/Upper.groovy");
        assertThat(svc.readScript("scripts/Upper.groovy")).isEqualTo("// upper");

        assertThat(svc.listMessages()).containsExactly("sample");
        MessageFixture fx = svc.readMessage("sample");
        assertThat(fx.body()).isEqualTo("<a/>");
        assertThat(fx.contentType()).isEqualTo("application/xml");
        assertThat(fx.headers()).containsEntry("H1", "v1");
        assertThat(fx.properties()).containsEntry("P1", 2);
    }

    @Test
    void rejectsPathTraversal_outsideWorkspace(@TempDir Path ws) {
        WorkspaceService svc = new WorkspaceService(ws.toString());
        assertThatThrownBy(() -> svc.readScript("../secrets.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void savesMessageFixture_asBodyFileAndYaml_thenReadsItBack(@TempDir Path ws) {
        WorkspaceService svc = new WorkspaceService(ws.toString());

        svc.saveMessage("neworder", "{\"a\":1}", "application/json",
                Map.of("SenderId", "ACME"), Map.of("attempt", 2));

        // Body written with a content-type-appropriate extension; listed; round-trips.
        assertThat(Files.exists(ws.resolve("messages/neworder/body.json"))).isTrue();
        assertThat(svc.listMessages()).contains("neworder");
        MessageFixture fx = svc.readMessage("neworder");
        assertThat(fx.body()).isEqualTo("{\"a\":1}");
        assertThat(fx.contentType()).isEqualTo("application/json");
        assertThat(fx.headers()).containsEntry("SenderId", "ACME");
        assertThat(fx.properties()).containsEntry("attempt", 2);
    }

    @Test
    void saveMessage_rejectsBadName(@TempDir Path ws) {
        WorkspaceService svc = new WorkspaceService(ws.toString());
        assertThatThrownBy(() -> svc.saveMessage("../evil", "x", "text/plain", Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void savesRunCase_asYaml_thenReadsItBack(@TempDir Path ws) {
        WorkspaceService svc = new WorkspaceService(ws.toString());

        RunCase saved = new RunCase(
                "uppercase-happy",
                "Upper.groovy",
                new MessageSpec("hello world", "text/plain", Map.of("In", "1"), Map.of("P", "x")),
                List.of(
                        Assertion.of(Kind.STATUS, "OK"),
                        Assertion.of(Kind.BODY_EQUALS, "HELLO WORLD"),
                        Assertion.of(Kind.HEADER, "Content-Type", "text/plain")));
        svc.saveCase(saved);

        assertThat(Files.exists(ws.resolve("cases/uppercase-happy.yaml"))).isTrue();
        assertThat(svc.listCases()).containsExactly("uppercase-happy");

        RunCase back = svc.readCase("uppercase-happy");
        assertThat(back.script()).isEqualTo("Upper.groovy");
        assertThat(back.message().body()).isEqualTo("hello world");
        assertThat(back.message().contentType()).isEqualTo("text/plain");
        assertThat(back.message().headers()).containsEntry("In", "1");
        assertThat(back.assertions()).containsExactly(
                new Assertion(Kind.STATUS, null, "OK"),
                new Assertion(Kind.BODY_EQUALS, null, "HELLO WORLD"),
                new Assertion(Kind.HEADER, "Content-Type", "text/plain"));
    }

    @Test
    void saveCase_rejectsBadName(@TempDir Path ws) {
        WorkspaceService svc = new WorkspaceService(ws.toString());
        RunCase bad = new RunCase("../evil", "X.groovy", new MessageSpec("", null, Map.of(), Map.of()), List.of());
        assertThatThrownBy(() -> svc.saveCase(bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsServices_fromServicesYaml(@TempDir Path ws) throws Exception {
        Files.writeString(ws.resolve("services.yaml"),
                "valueMappings:\n"
                        + "  - sourceAgency: C4C\n"
                        + "    sourceIdentifier: Country\n"
                        + "    sourceValue: yMKT\n"
                        + "    targetAgency: Country\n"
                        + "    targetIdentifier: Austria\n"
                        + "    value: AT\n"
                        + "credentials:\n"
                        + "  MyAlias:\n"
                        + "    username: user1\n"
                        + "    password: secret1\n");
        WorkspaceService svc = new WorkspaceService(ws.toString());

        var registry = svc.readServices().registry();
        var vm = (com.sap.it.api.mapping.ValueMappingApi) registry.get(com.sap.it.api.mapping.ValueMappingApi.class);
        var store = (com.sap.it.api.securestore.SecureStoreService) registry.get(com.sap.it.api.securestore.SecureStoreService.class);

        assertThat(vm.getMappedValue("C4C", "Country", "yMKT", "Country", "Austria")).isEqualTo("AT");
        var cred = store.getUserCredential("MyAlias");
        assertThat(cred.getUsername()).isEqualTo("user1");
        assertThat(new String(cred.getPassword())).isEqualTo("secret1");
    }

    @Test
    void readServices_missingFile_returnsEmpty(@TempDir Path ws) {
        WorkspaceService svc = new WorkspaceService(ws.toString());
        assertThat(svc.readServices().registry()).isNotEmpty(); // instances present…
        var vm = (com.sap.it.api.mapping.ValueMappingApi)
                svc.readServices().registry().get(com.sap.it.api.mapping.ValueMappingApi.class);
        assertThat(vm.getMappedValue("a", "b", "c", "d", "e")).isNull(); // …but they resolve nothing
    }
}
