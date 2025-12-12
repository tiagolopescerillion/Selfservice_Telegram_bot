package com.selfservice.application.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class ServiceCatalog {

    private static final Logger log = LoggerFactory.getLogger(ServiceCatalog.class);

    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final String LOCAL_FILE = "services-local.yml";
    private static final String DEFAULT_FILE = "services-default.yml";

    private final List<ServiceDefinition> services;

    public ServiceCatalog() {
        this.services = Collections.unmodifiableList(loadServices());
    }

    public List<ServiceDefinition> getServices() {
        return services;
    }

    public Optional<ServiceDefinition> findByName(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        String normalized = slugify(name);
        return services.stream()
                .filter(service -> service.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public Map<String, ServiceDefinition> getServiceByName() {
        return services.stream().collect(Collectors.toMap(ServiceDefinition::name, service -> service));
    }

    private List<ServiceDefinition> loadServices() {
        Path source = resolveConfigPath();
        if (source == null) {
            log.warn("No service configuration file found in {}", CONFIG_DIR.toAbsolutePath());
            return List.of();
        }

        try (InputStream input = Files.newInputStream(source)) {
            Object loaded = new Yaml().load(input);
            if (!(loaded instanceof Map<?, ?> rawMap)) {
                log.warn("Service configuration at {} is empty or invalid", source);
                return List.of();
            }
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> map.put(String.valueOf(key), value));

            Object entries = map.containsKey("services") ? map.get("services") : List.of();
            if (!(entries instanceof Iterable<?> iterable)) {
                log.warn("Service configuration at {} does not contain a 'services' list", source);
                return List.of();
            }
            return parseServices(iterable);
        } catch (IOException ex) {
            log.error("Failed to read service configuration {}: {}", source, ex.getMessage());
            return List.of();
        }
    }

    private List<ServiceDefinition> parseServices(Iterable<?> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false)
                .map(this::coerceServiceDefinition)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Optional<ServiceDefinition> coerceServiceDefinition(Object entry) {
        if (entry instanceof Map<?, ?> raw) {
            Map<String, Object> values = new LinkedHashMap<>((Map<String, Object>) raw);
            String name = normalize(values.get("Service Name"));
            String apiName = normalize(values.get("API-Name"));
            Object queryParams = values.getOrDefault("Query Parameters", Map.of());
            String responseTemplate = normalize(values.get("Response Template"));

            if (!StringUtils.hasText(name) || !StringUtils.hasText(apiName)) {
                return Optional.empty();
            }
            Map<String, String> params = new LinkedHashMap<>();
            if (queryParams instanceof Map<?, ?> queryMap) {
                queryMap.forEach((key, value) -> params.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
            }
            ResponseTemplate template = ResponseTemplate.fromLabel(responseTemplate);
            return Optional.of(new ServiceDefinition(slugify(name), slugify(apiName), params, template));
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
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    public enum ResponseTemplate {
        EXISTING,
        JSON;

        public static ResponseTemplate fromLabel(String label) {
            if (!StringUtils.hasText(label)) {
                return JSON;
            }
            String normalized = label.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "EXISTING", "LEGACY" -> EXISTING;
                default -> JSON;
            };
        }
    }

    public record ServiceDefinition(String name, String apiName, Map<String, String> queryParameters,
                                    ResponseTemplate responseTemplate) { }
}
