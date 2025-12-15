package com.selfservice.application.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class ApiRegistry {

    private static final Logger log = LoggerFactory.getLogger(ApiRegistry.class);

    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final String LOCAL_FILE = "API-list-local.yml";
    private static final String DEFAULT_FILE = "API-list-default.yml";

    private final AtomicReference<List<ApiDefinition>> apis = new AtomicReference<>(List.of());

    public ApiRegistry() {
        reload();
    }

    public List<ApiDefinition> getApis() {
        return apis.get();
    }

    public Map<String, ApiDefinition> getApisByName() {
        return apis.stream().collect(Collectors.toMap(ApiDefinition::name, api -> api));
    }

    public Optional<ApiDefinition> findByName(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        String normalized = slugify(name);
        return getApis().stream()
                .filter(api -> api.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public synchronized List<ApiDefinition> reload() {
        List<ApiDefinition> loaded = Collections.unmodifiableList(loadApis());
        apis.set(loaded);
        return loaded;
    }

    public synchronized List<ApiDefinition> saveApis(List<ApiDefinition> definitions) throws IOException {
        List<ApiDefinition> payload = definitions == null ? List.of() : definitions;
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("apis", payload.stream()
                .map(api -> Map.of(
                        "API-name", api.name(),
                        "API-URL", api.url()
                ))
                .toList());

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        String serialized = yaml.dump(document);

        Files.createDirectories(CONFIG_DIR);
        Files.writeString(CONFIG_DIR.resolve(LOCAL_FILE), serialized, StandardCharsets.UTF_8);
        return reload();
    }

    private List<ApiDefinition> loadApis() {
        Path source = resolveConfigPath();
        if (source == null) {
            log.warn("No API configuration file found in {}", CONFIG_DIR.toAbsolutePath());
            return List.of();
        }

        try (InputStream input = Files.newInputStream(source)) {
            Object loaded = new Yaml().load(input);
            if (!(loaded instanceof Map<?, ?> rawMap)) {
                log.warn("API configuration at {} is empty or invalid", source);
                return List.of();
            }
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> map.put(String.valueOf(key), value));

            Object entries = map.containsKey("apis") ? map.get("apis") : List.of();
            if (!(entries instanceof Iterable<?> iterable)) {
                log.warn("API configuration at {} does not contain an 'apis' list", source);
                return List.of();
            }
            return parseApis(iterable);
        } catch (IOException ex) {
            log.error("Failed to read API configuration {}: {}", source, ex.getMessage());
            return List.of();
        }
    }

    private List<ApiDefinition> parseApis(Iterable<?> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false)
                .map(entry -> coerceApiDefinition(entry))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Optional<ApiDefinition> coerceApiDefinition(Object entry) {
        if (entry instanceof Map<?, ?> raw) {
            Map<String, Object> values = new LinkedHashMap<>((Map<String, Object>) raw);
            String name = normalize(values.get("API-name"));
            String url = normalize(values.get("API-URL"));
            if (name == null || url == null) {
                return Optional.empty();
            }
            return Optional.of(new ApiDefinition(slugify(name), url));
        }
        return Optional.empty();
    }

    private Path resolveConfigPath() {
        Path local = CONFIG_DIR.resolve(LOCAL_FILE);
        if (Files.exists(local)) {
            return local;
        }
        Path defaults = CONFIG_DIR.resolve(DEFAULT_FILE);
        if (Files.exists(defaults)) {
            return defaults;
        }
        return null;
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String slugify(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    public record ApiDefinition(String name, String url) { }
}
