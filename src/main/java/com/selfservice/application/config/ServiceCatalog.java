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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class ServiceCatalog {

    private static final Logger log = LoggerFactory.getLogger(ServiceCatalog.class);

    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final String LOCAL_FILE = "services-local.yml";
    private static final String DEFAULT_FILE = "services-default.yml";

    private final AtomicReference<List<ServiceDefinition>> services = new AtomicReference<>(List.of());

    public ServiceCatalog() {
        reload();
    }

    public List<ServiceDefinition> getServices() {
        return services.get();
    }

    public Optional<ServiceDefinition> findByName(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        String normalized = slugify(name);
        return getServices().stream()
                .filter(service -> service.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public Map<String, ServiceDefinition> getServiceByName() {
        return getServices().stream().collect(Collectors.toMap(ServiceDefinition::name, service -> service));
    }

    public synchronized List<ServiceDefinition> reload() {
        List<ServiceDefinition> loaded = Collections.unmodifiableList(loadServices());
        services.set(loaded);
        return loaded;
    }

    public synchronized List<ServiceDefinition> saveServices(List<ServiceDefinition> definitions) throws IOException {
        List<ServiceDefinition> payload = definitions == null ? List.of() : definitions;
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("services", payload.stream()
                .map(this::serializeService)
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

    private Map<String, Object> serializeService(ServiceDefinition service) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Service Name", Optional.ofNullable(service.name()).orElse(""));
        entry.put("API-Name", Optional.ofNullable(service.apiName()).orElse(""));
        entry.put("Query Parameters", sanitizeQueryParameters(service.queryParameters()));
        if (StringUtils.hasText(service.accountContextField())) {
            entry.put("Account Context Field", service.accountContextField());
        }
        if (StringUtils.hasText(service.serviceContextField())) {
            entry.put("Service Context Field", service.serviceContextField());
        }
        if (StringUtils.hasText(service.objectContextField())) {
            entry.put("Object Context Field", service.objectContextField());
        }
        ResponseTemplate template = Optional.ofNullable(service.responseTemplate()).orElse(ResponseTemplate.JSON);
        entry.put("Response Template", template.name());
        entry.put("Output", serializeOutputs(service.outputs()));
        return entry;
    }

    private List<Map<String, Object>> serializeOutputs(List<OutputField> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return List.of();
        }
        return outputs.stream()
                .filter(Objects::nonNull)
                .map(field -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("Field", Optional.ofNullable(field.field()).orElse(""));
                    map.put("Label", Optional.ofNullable(field.label()).orElse(""));
                    map.put("Object Context", field.objectContext());
                    return map;
                })
                .toList();
    }

    private Map<String, String> sanitizeQueryParameters(Map<String, String> parameters) {
        if (parameters == null) {
            return Map.of();
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        parameters.forEach((key, value) -> cleaned.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
        return cleaned;
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
            String accountContextField = normalize(values.get("Account Context Field"));
            String serviceContextField = normalize(values.get("Service Context Field"));
            String objectContextField = normalize(values.get("Object Context Field"));
            String responseTemplate = normalize(values.get("Response Template"));
            Object rawOutput = values.get("Output");

            if (!StringUtils.hasText(name) || !StringUtils.hasText(apiName)) {
                return Optional.empty();
            }
            Map<String, String> params = new LinkedHashMap<>();
            if (queryParams instanceof Map<?, ?> queryMap) {
                queryMap.forEach((key, value) -> params.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
            }
            ResponseTemplate template = ResponseTemplate.fromLabel(responseTemplate);
            List<OutputField> outputs = parseOutputs(rawOutput);
            return Optional.of(new ServiceDefinition(slugify(name), slugify(apiName), params, template, outputs,
                    accountContextField, serviceContextField, objectContextField));
        }
        return Optional.empty();
    }

    private List<OutputField> parseOutputs(Object rawOutput) {
        if (rawOutput == null) {
            return List.of();
        }
        if (rawOutput instanceof String rawString) {
            if (!StringUtils.hasText(rawString)) {
                return List.of();
            }
            return sanitizeOutputs(List.of(rawString.split(",")));
        }
        if (rawOutput instanceof List<?> list) {
            if (list.isEmpty()) {
                return List.of();
            }
            boolean containsMap = list.stream().anyMatch(Map.class::isInstance);
            if (containsMap) {
                return sanitizeOutputs(list.stream().map(this::mapToOutputField).filter(Objects::nonNull).toList());
            }
            return sanitizeOutputs(list.stream().map(Object::toString).toList());
        }
        if (rawOutput instanceof Map<?, ?> map) {
            OutputField field = mapToOutputField(map);
            return field == null ? List.of() : List.of(field);
        }
        return List.of();
    }

    private OutputField mapToOutputField(Object entry) {
        if (!(entry instanceof Map<?, ?> raw)) {
            return null;
        }
        String field = normalize(raw.get("Field"));
        String label = normalize(raw.get("Label"));
        Object rawObjectContext = raw.containsKey("Object Context") ? raw.get("Object Context") : Boolean.FALSE;
        boolean objectContext = Boolean.parseBoolean(String.valueOf(rawObjectContext));
        if (!StringUtils.hasText(field)) {
            return null;
        }
        return new OutputField(field, StringUtils.hasText(label) ? label : field, objectContext);
    }

    private List<OutputField> sanitizeOutputs(List<?> rawOutputs) {
        if (rawOutputs == null || rawOutputs.isEmpty()) {
            return List.of();
        }
        List<OutputField> parsed = rawOutputs.stream()
                .map(value -> {
                    if (value instanceof OutputField outputField) {
                        return outputField;
                    }
                    String path = normalize(value);
                    if (!StringUtils.hasText(path)) {
                        return null;
                    }
                    return new OutputField(path, path, false);
                })
                .filter(Objects::nonNull)
                .toList();

        AtomicBoolean objectContextFound = new AtomicBoolean(false);
        return parsed.stream()
                .map(field -> {
                    boolean objectContext = field.objectContext();
                    if (objectContext && objectContextFound.get()) {
                        objectContext = false;
                    }
                    if (objectContext) {
                        objectContextFound.set(true);
                    }
                    String label = StringUtils.hasText(field.label()) ? field.label() : field.field();
                    return new OutputField(field.field(), label, objectContext);
                })
                .toList();
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
        JSON,
        MESSAGE,
        CARD;

        public static ResponseTemplate fromLabel(String label) {
            if (!StringUtils.hasText(label)) {
                return JSON;
            }
            String normalized = label.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "EXISTING", "LEGACY" -> EXISTING;
                case "MESSAGE" -> MESSAGE;
                case "CARD" -> CARD;
                case "JSON" -> JSON;
                default -> JSON;
            };
        }
    }

    public record ServiceDefinition(String name, String apiName, Map<String, String> queryParameters,
                                    ResponseTemplate responseTemplate, List<OutputField> outputs,
                                    String accountContextField, String serviceContextField, String objectContextField) { }

    public record OutputField(String field, String label, boolean objectContext) { }
}
