package com.iflowmonitor.iflowlab.app;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.yaml.snakeyaml.Yaml;

/**
 * Read access to the workspace directory — the filesystem is the source of
 * truth (D4). Guards every path against traversal outside the workspace root.
 */
@ApplicationScoped
public class WorkspaceService {

    private final Path root;

    @Inject
    public WorkspaceService(@ConfigProperty(name = "iflowlab.workspace") String workspace) {
        this.root = Path.of(workspace).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** All {@code *.groovy} files anywhere in the workspace, as workspace-relative posix paths. */
    public List<String> listScripts() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".groovy"))
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String readScript(String relPath) {
        return read(resolve(relPath));
    }

    /** Names of the {@code messages/<name>/} fixtures. */
    public List<String> listMessages() {
        Path dir = root.resolve("messages");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public MessageFixture readMessage(String name) {
        Path dir = resolve("messages/" + name);
        Path bodyFile = findBodyFile(dir);
        String body = bodyFile == null ? "" : read(bodyFile);

        String contentType = null;
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        Path metaFile = dir.resolve("message.yaml");
        if (Files.isRegularFile(metaFile)) {
            Map<String, Object> meta = new Yaml().load(read(metaFile));
            if (meta != null) {
                contentType = meta.get("contentType") == null ? null : meta.get("contentType").toString();
                if (meta.get("headers") instanceof Map<?, ?> h) {
                    ((Map<String, Object>) h).forEach(headers::put);
                }
                if (meta.get("properties") instanceof Map<?, ?> pr) {
                    ((Map<String, Object>) pr).forEach(properties::put);
                }
            }
        }
        return new MessageFixture(name, body, contentType, headers, properties);
    }

    private static Path findBodyFile(Path dir) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("body."))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String relPath) {
        Path resolved = root.resolve(relPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("path escapes the workspace: " + relPath);
        }
        return resolved;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
