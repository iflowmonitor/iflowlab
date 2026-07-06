package com.iflowmonitor.iflowlab.app;

import com.iflowmonitor.iflowlab.app.cases.MessageSpec;
import com.iflowmonitor.iflowlab.app.cases.RunCase;
import com.iflowmonitor.iflowlab.cpimock.services.CpiServices;
import com.iflowmonitor.iflowlab.cpimock.services.ValueMappingEntry;
import com.iflowmonitor.iflowlab.engine.assertions.Assertion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    /** Runnable scripts anywhere in the workspace ({@code *.groovy}, {@code *.xsl(t)}), as posix paths. */
    public List<String> listScripts() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".groovy") || n.endsWith(".xsl") || n.endsWith(".xslt");
                    })
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

    /** Back-compat: save a fixture with no attachments. */
    public void saveMessage(
            String name, String body, String contentType, Map<String, Object> headers, Map<String, Object> properties) {
        saveMessage(name, body, contentType, headers, properties, List.of());
    }

    /** Persists a {@code messages/<name>/} fixture: a body file + message.yaml + attachment files (R5, slice 8). */
    public void saveMessage(
            String name, String body, String contentType, Map<String, Object> headers, Map<String, Object> properties,
            List<MessageFixture.Attachment> attachments) {
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

            List<MessageFixture.Attachment> atts = attachments == null ? List.of() : attachments;
            if (!atts.isEmpty()) {
                Path attDir = dir.resolve("attachments");
                Files.createDirectories(attDir);
                List<Map<String, Object>> attMeta = new ArrayList<>();
                for (MessageFixture.Attachment a : atts) {
                    validateName(a.name());
                    Files.writeString(attDir.resolve(a.name()), a.body() == null ? "" : a.body(), StandardCharsets.UTF_8);
                    Map<String, Object> am = new LinkedHashMap<>();
                    am.put("name", a.name());
                    if (a.contentType() != null && !a.contentType().isBlank()) {
                        am.put("contentType", a.contentType());
                    }
                    attMeta.add(am);
                }
                meta.put("attachments", attMeta);
            }

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
        List<MessageFixture.Attachment> attachments = new ArrayList<>();
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
                if (meta.get("attachments") instanceof List<?> list) {
                    Path attDir = dir.resolve("attachments");
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> a) {
                            Map<String, Object> am = (Map<String, Object>) a;
                            String attName = am.get("name") == null ? null : am.get("name").toString();
                            if (attName != null) {
                                Path attFile = attDir.resolve(attName);
                                String attBody = Files.isRegularFile(attFile) ? read(attFile) : "";
                                String attCt = am.get("contentType") == null ? null : am.get("contentType").toString();
                                attachments.add(new MessageFixture.Attachment(attName, attBody, attCt));
                            }
                        }
                    }
                }
            }
        }
        return new MessageFixture(name, body, contentType, headers, properties, attachments);
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

    /** Names of the saved run-cases ({@code cases/<name>.yaml}), without extension. */
    public List<String> listCases() {
        Path dir = root.resolve("cases");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".yaml"))
                    .map(n -> n.substring(0, n.length() - ".yaml".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Persists a run-case as {@code cases/<name>.yaml}: script + inline message + assertions (slice 3). */
    public void saveCase(RunCase runCase) {
        validateName(runCase.name());
        Path file = resolve("cases/" + runCase.name() + ".yaml");
        try {
            Files.createDirectories(file.getParent());

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("script", runCase.script());

            MessageSpec msg = runCase.message();
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("body", msg == null || msg.body() == null ? "" : msg.body());
            if (msg != null && msg.contentType() != null && !msg.contentType().isBlank()) {
                message.put("contentType", msg.contentType());
            }
            message.put("headers", msg == null || msg.headers() == null ? Map.of() : msg.headers());
            message.put("properties", msg == null || msg.properties() == null ? Map.of() : msg.properties());
            meta.put("message", message);

            List<Map<String, Object>> assertions = new ArrayList<>();
            for (Assertion a : runCase.assertions()) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("kind", a.kind().name());
                if (a.target() != null && !a.target().isBlank()) {
                    am.put("target", a.target());
                }
                am.put("expected", a.expected() == null ? "" : a.expected());
                assertions.add(am);
            }
            meta.put("assertions", assertions);

            DumperOptions opts = new DumperOptions();
            opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            opts.setPrettyFlow(true);
            Files.writeString(file, new Yaml(opts).dump(meta), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public RunCase readCase(String name) {
        Path file = resolve("cases/" + name + ".yaml");
        Map<String, Object> meta = new Yaml().load(read(file));
        if (meta == null) {
            throw new IllegalArgumentException("empty case: " + name);
        }
        String script = meta.get("script") == null ? null : meta.get("script").toString();

        MessageSpec message = null;
        if (meta.get("message") instanceof Map<?, ?> m) {
            Map<String, Object> mm = (Map<String, Object>) m;
            String body = mm.get("body") == null ? "" : mm.get("body").toString();
            String contentType = mm.get("contentType") == null ? null : mm.get("contentType").toString();
            Map<String, Object> headers = mm.get("headers") instanceof Map<?, ?> h
                    ? new LinkedHashMap<>((Map<String, Object>) h) : new LinkedHashMap<>();
            Map<String, Object> properties = mm.get("properties") instanceof Map<?, ?> pr
                    ? new LinkedHashMap<>((Map<String, Object>) pr) : new LinkedHashMap<>();
            message = new MessageSpec(body, contentType, headers, properties);
        }

        List<Assertion> assertions = new ArrayList<>();
        if (meta.get("assertions") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> a) {
                    Map<String, Object> am = (Map<String, Object>) a;
                    Assertion.Kind kind = Assertion.Kind.valueOf(am.get("kind").toString());
                    String target = am.get("target") == null ? null : am.get("target").toString();
                    String expected = am.get("expected") == null ? "" : am.get("expected").toString();
                    assertions.add(new Assertion(kind, target, expected));
                }
            }
        }
        return new RunCase(name, script, message, assertions);
    }

    /**
     * The CPI platform-service mocks seeded from {@code services.yaml} at the workspace
     * root (value mappings + secure-store credentials). Absent file → no services (slice 4).
     */
    @SuppressWarnings("unchecked")
    public CpiServices readServices() {
        Path file = root.resolve("services.yaml");
        if (!Files.isRegularFile(file)) {
            return CpiServices.EMPTY;
        }
        Map<String, Object> meta = new Yaml().load(read(file));
        if (meta == null) {
            return CpiServices.EMPTY;
        }

        List<ValueMappingEntry> valueMappings = new ArrayList<>();
        if (meta.get("valueMappings") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> vm = (Map<String, Object>) m;
                    valueMappings.add(new ValueMappingEntry(
                            str(vm, "sourceAgency"), str(vm, "sourceIdentifier"), str(vm, "sourceValue"),
                            str(vm, "targetAgency"), str(vm, "targetIdentifier"), str(vm, "value")));
                }
            }
        }

        Map<String, String[]> credentials = new LinkedHashMap<>();
        if (meta.get("credentials") instanceof Map<?, ?> creds) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) creds).entrySet()) {
                if (e.getValue() instanceof Map<?, ?> up) {
                    Map<String, Object> upm = (Map<String, Object>) up;
                    credentials.put(e.getKey(), new String[] {str(upm, "username"), str(upm, "password")});
                }
            }
        }
        return new CpiServices(valueMappings, credentials);
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
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
