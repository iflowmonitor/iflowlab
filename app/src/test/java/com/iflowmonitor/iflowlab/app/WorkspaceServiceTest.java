package com.iflowmonitor.iflowlab.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
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
}
