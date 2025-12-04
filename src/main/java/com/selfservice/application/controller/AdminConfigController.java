package com.selfservice.application.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/admin")
@CrossOrigin
public class AdminConfigController {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigController.class);

    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final Map<String, List<String>> CONFIG_FILES = Map.of(
            "application", List.of("application-local.yml", "application-example.yml"),
            "connectors", List.of("connectors-local.yml", "connectors-example.yml"),
            "telegram", List.of("telegram-local.yml", "telegram-example.yml"),
            "whatsapp", List.of("whatsapp-local.yml", "whatsapp-example.yml"),
            "messenger", List.of("messenger-local.yml", "messenger-example.yml")
    );

    @GetMapping("/application-config")
    public ResponseEntity<ApplicationConfigView> getApplicationConfig() {
        return loadConfig("application");
    }

    @GetMapping("/config/{configId}")
    public ResponseEntity<ApplicationConfigView> getNamedConfig(@PathVariable String configId) {
        return loadConfig(configId == null ? "" : configId.trim().toLowerCase());
    }

    private ResponseEntity<ApplicationConfigView> loadConfig(String configId) {
        Optional<Path> selected = resolveConfigPath(configId);
        if (selected.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Path configPath = selected.get();
        try {
            String content = Files.readString(configPath, StandardCharsets.UTF_8);
            List<ConfigEntry> entries = parseEntries(content);
            List<ConfigNode> tree = parseTree(content);
            return ResponseEntity.ok(new ApplicationConfigView(
                    configPath.getFileName().toString(),
                    content,
                    Files.getLastModifiedTime(configPath).toMillis(),
                    entries,
                    tree
            ));
        } catch (IOException e) {
            log.error("Unable to read configuration file {}: {}", configPath, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApplicationConfigView(configPath.getFileName().toString(), "", 0L, List.of(), List.of()));
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

    private Optional<Path> resolveConfigPath(String configId) {
        List<String> candidates = CONFIG_FILES.get(configId);
        if (candidates == null || candidates.isEmpty()) {
            log.warn("Unsupported configuration requested: {}", configId);
            return Optional.empty();
        }

        for (String candidate : candidates) {
            Path resolved = CONFIG_DIR.resolve(candidate);
            if (Files.exists(resolved)) {
                return Optional.of(resolved);
            }
        }

        log.warn("No configuration file found in {} for {} (looked for {})", CONFIG_DIR, configId, String.join(", ", candidates));
        return Optional.empty();
    }

    public record ApplicationConfigView(String fileName, String content, long lastModified, List<ConfigEntry> entries,
                                        List<ConfigNode> tree) { }

    public record ConfigEntry(String key, String value, String type) { }

    public record ConfigNode(String key, String path, String type, String value, List<ConfigNode> children) { }
}
