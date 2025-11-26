package com.selfservice.telegrambot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
@CrossOrigin
public class AdminConfigController {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigController.class);

    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final String LOCAL_CONFIG = "application-local.yml";

    @GetMapping("/application-config")
    public ResponseEntity<ApplicationConfigView> getApplicationConfig() {
        Optional<ResolvedConfig> selected = resolveConfig();
        if (selected.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        ResolvedConfig config = selected.get();
        try {
            String content = readResource(config.resource());
            List<ConfigEntry> entries = parseEntries(content);
            List<ConfigNode> tree = parseTree(content);
            return ResponseEntity.ok(new ApplicationConfigView(
                    config.fileName(),
                    content,
                    config.lastModified(),
                    entries,
                    tree
            ));
        } catch (IOException e) {
            log.error("Unable to read configuration file {}: {}", config.fileName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApplicationConfigView(config.fileName(), "", 0L, List.of(), List.of()));
        }
    }

    private String readResource(Resource resource) throws IOException {
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<ConfigEntry> parseEntries(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        try {
            Object loaded = new Yaml().load(content);
            List<ConfigEntry> entries = new ArrayList<>();
            flattenYaml("", loaded, entries);
            return entries;
        } catch (Exception ex) {
            log.warn("Unable to parse YAML for admin view: {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private void flattenYaml(String prefix, Object node, List<ConfigEntry> entries) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                String childPrefix = prefix.isBlank() ? String.valueOf(key) : prefix + "." + key;
                flattenYaml(childPrefix, value, entries);
            });
            return;
        }

        if (node instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object item : iterable) {
                String childPrefix = prefix + "[" + index++ + "]";
                flattenYaml(childPrefix, item, entries);
            }
            return;
        }

        String key = prefix.isBlank() ? "value" : prefix;
        String value = node == null ? "" : String.valueOf(node);
        entries.add(new ConfigEntry(key, value, detectType(node)));
    }

    private String detectType(Object node) {
        if (node instanceof Boolean) {
            return "boolean";
        }
        if (node instanceof Number) {
            return "number";
        }
        return "string";
    }

    private List<ConfigNode> parseTree(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        try {
            Object loaded = new Yaml().load(content);
            return buildTree(loaded, "");
        } catch (Exception ex) {
            log.warn("Unable to parse YAML tree for admin view: {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ConfigNode> buildTree(Object node, String path) {
        if (node instanceof Map<?, ?> map) {
            List<ConfigNode> children = new ArrayList<>();
            map.forEach((key, value) -> {
                String childPath = path.isBlank() ? String.valueOf(key) : path + "." + key;
                children.add(buildBranch(String.valueOf(key), childPath, value));
            });
            return children;
        }

        if (node instanceof Iterable<?> iterable) {
            List<ConfigNode> children = new ArrayList<>();
            int index = 0;
            for (Object item : iterable) {
                String childKey = "[" + index + "]";
                String childPath = path + childKey;
                children.add(buildBranch(childKey, childPath, item));
                index++;
            }
            return children;
        }

        String key = path.isBlank() ? "value" : path;
        return List.of(new ConfigNode(key, path, detectType(node), node == null ? "" : String.valueOf(node), List.of()));
    }

    @SuppressWarnings("unchecked")
    private ConfigNode buildBranch(String key, String path, Object value) {
        if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            List<ConfigNode> children = buildTree(value, path);
            return new ConfigNode(key, path, "object", "", children);
        }
        return new ConfigNode(key, path, detectType(value), value == null ? "" : String.valueOf(value), List.of());
    }

    private Optional<ResolvedConfig> resolveConfig() {
        Optional<ResolvedConfig> resolved = findOnFileSystem(LOCAL_CONFIG)
                .or(() -> findOnClasspath(LOCAL_CONFIG));

        if (resolved.isEmpty()) {
            log.warn("No configuration file found in {} (looked for {})", CONFIG_DIR, LOCAL_CONFIG);
        }

        return resolved;
    }

    private Optional<ResolvedConfig> findOnFileSystem(String fileName) {
        Path candidate = CONFIG_DIR.resolve(fileName);
        if (!Files.exists(candidate)) {
            return Optional.empty();
        }

        Resource resource = new FileSystemResource(candidate);
        try {
            long lastModified = Files.getLastModifiedTime(candidate).toMillis();
            return Optional.of(new ResolvedConfig(fileName, resource, lastModified));
        } catch (IOException ex) {
            log.warn("Unable to resolve last modified time for {}: {}", candidate, ex.getMessage());
            return Optional.of(new ResolvedConfig(fileName, resource, 0L));
        }
    }

    private Optional<ResolvedConfig> findOnClasspath(String fileName) {
        String location = CONFIG_DIR.resolve(fileName).toString();
        Resource resource = new ClassPathResource(location);
        if (!resource.exists()) {
            return Optional.empty();
        }

        long lastModified = 0L;
        try {
            lastModified = resource.lastModified();
        } catch (IOException ex) {
            log.debug("Unable to read last modified time for classpath resource {}: {}", location, ex.getMessage());
        }

        return Optional.of(new ResolvedConfig(fileName, resource, lastModified));
    }

    public record ApplicationConfigView(String fileName, String content, long lastModified, List<ConfigEntry> entries,
                                        List<ConfigNode> tree) { }

    public record ConfigEntry(String key, String value, String type) { }

    public record ConfigNode(String key, String path, String type, String value, List<ConfigNode> children) { }

    public record ResolvedConfig(String fileName, Resource resource, long lastModified) { }
}
