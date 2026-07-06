package com.iflowmonitor.iflowlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** MRU list of workspace roots, persisted to a store file (slice 9). */
class RecentWorkspacesTest {

    @Test
    void recordsMostRecentFirst_andDedups(@TempDir Path dir) {
        RecentWorkspaces recents = new RecentWorkspaces(dir.resolve("recents.txt"));
        recents.record("/a");
        recents.record("/b");
        recents.record("/a"); // touch again → moves to front, no duplicate

        assertThat(recents.list()).containsExactly("/a", "/b");
    }

    @Test
    void persistsAcrossInstances(@TempDir Path dir) {
        Path store = dir.resolve("recents.txt");
        RecentWorkspaces first = new RecentWorkspaces(store);
        first.record("/x");
        first.record("/y");

        RecentWorkspaces reopened = new RecentWorkspaces(store);
        assertThat(reopened.list()).containsExactly("/y", "/x");
    }

    @Test
    void capsAtEightEntries(@TempDir Path dir) {
        RecentWorkspaces recents = new RecentWorkspaces(dir.resolve("recents.txt"));
        for (int i = 0; i < 12; i++) {
            recents.record("/ws" + i);
        }
        assertThat(recents.list()).hasSize(8).startsWith("/ws11", "/ws10");
    }

    @Test
    void nullStore_isInMemoryOnly(@TempDir Path dir) {
        RecentWorkspaces recents = new RecentWorkspaces((Path) null);
        recents.record("/only");
        assertThat(recents.list()).containsExactly("/only");
    }
}
