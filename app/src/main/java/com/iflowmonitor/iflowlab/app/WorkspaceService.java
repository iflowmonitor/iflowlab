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
import org.yaml.snakeyaml.DumperOptions;
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

    /** Persists a {@code messages/<name>/} fixture: a body file (extension by content-type) + message.yaml (R5). */
    public void saveMessage(
            String name, String body, String contentType, Map<String, Object> headers, Map<String, Object> properties) {
        validateName(name);
        Path dir = resolve("messages/" + name);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("body." + extForContentType(contentType)),
                    body == null ? "" : body, StandardCharsets.UTF_8);

            Map<String, Object> meta = new LinkedHashMap<>();
            if (contentType != null && !contentType.isBlank()) {
                meta.put("contentType", contentType);
            }
            meta.put("headers", headers == null ? Map.of() : headers);
            meta.put("properties", properties == null ? Map.of() : properties);

            DumperOptions opts = new DumperOptions();
            opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            opts.setPrettyFlow(true);
            Files.writeString(dir.resolve("message.yaml"), new Yaml(opts).dump(meta), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void validateName(String name) {
        // A fixture name is a single path segment — no separators or traversal.
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("invalid fixture name: " + name);
        }
    }

    private static String extForContentType(String contentType) {
        if (contentType == null) {
            return "txt";
        }
        String ct = contentType.toLowerCase();
        if (ct.contains("json")) {
            return "json";
        }
        if (ct.contains("xml")) {
            return "xml";
        }
        if (ct.startsWith("text/")) {
            return "txt";
        }
        return "bin";
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
