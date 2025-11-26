package com.selfservice.telegrambot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String EXAMPLE_CONFIG = "application-example.yml";

    @GetMapping("/application-config")
    public ResponseEntity<ApplicationConfigView> getApplicationConfig() {
        Optional<Path> selected = resolveConfigPath();
        if (selected.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Path configPath = selected.get();
        try {
            String content = Files.readString(configPath, StandardCharsets.UTF_8);
            List<ConfigEntry> entries = parseEntries(content);
            return ResponseEntity.ok(new ApplicationConfigView(
                    configPath.getFileName().toString(),
                    content,
                    Files.getLastModifiedTime(configPath).toMillis(),
                    entries
            ));
        } catch (IOException e) {
            log.error("Unable to read configuration file {}: {}", configPath, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApplicationConfigView(configPath.getFileName().toString(), "", 0L, List.of()));
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

    private Optional<Path> resolveConfigPath() {
        Path local = CONFIG_DIR.resolve(LOCAL_CONFIG);
        if (Files.exists(local)) {
            return Optional.of(local);
        }

        Path example = CONFIG_DIR.resolve(EXAMPLE_CONFIG);
        if (Files.exists(example)) {
            return Optional.of(example);
        }

        log.warn("No configuration file found in {} (looked for {} and {})", CONFIG_DIR, LOCAL_CONFIG, EXAMPLE_CONFIG);
        return Optional.empty();
    }

    public record ApplicationConfigView(String fileName, String content, long lastModified, List<ConfigEntry> entries) { }

    public record ConfigEntry(String key, String value, String type) { }
}
