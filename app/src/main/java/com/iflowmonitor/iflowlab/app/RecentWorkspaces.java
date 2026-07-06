package com.iflowmonitor.iflowlab.app;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A most-recently-used list of workspace roots, persisted line-per-path to a
 * store file so it survives restarts. A {@code null} store keeps it in-memory
 * only (used by tests that shouldn't touch the user's home). Deep module:
 * the whole surface is {@link #record(String)} and {@link #list()}.
 */
@ApplicationScoped
public class RecentWorkspaces {

    private static final int MAX = 8;

    private final Path store; // nullable → in-memory only
    private final Deque<String> entries = new ArrayDeque<>();

    @Inject
    public RecentWorkspaces(@ConfigProperty(name = "iflowlab.recents-file") String storeFile) {
        this(resolveStore(storeFile));
    }

    public RecentWorkspaces(Path store) {
        this.store = store;
        load();
    }

    private static Path resolveStore(String storeFile) {
        if (storeFile != null && !storeFile.isBlank()) {
            return Path.of(storeFile);
        }
        return Path.of(System.getProperty("user.home", "."), ".iflowlab", "recent-workspaces.txt");
    }

    /** Move {@code path} to the front (dedup), cap the list, and persist. */
    public synchronized void record(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        entries.remove(path);
        entries.addFirst(path);
        while (entries.size() > MAX) {
            entries.removeLast();
        }
        save();
    }

    /** The roots in most-recent-first order. */
    public synchronized List<String> list() {
        return List.copyOf(entries);
    }

    private void load() {
        if (store == null || !Files.isRegularFile(store)) {
            return;
        }
        try {
            // File is stored oldest→newest? No: we write most-recent-first, so read as-is.
            for (String line : Files.readAllLines(store, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    entries.addLast(line.strip());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void save() {
        if (store == null) {
            return;
        }
        try {
            if (store.getParent() != null) {
                Files.createDirectories(store.getParent());
            }
            Files.write(store, entries, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
