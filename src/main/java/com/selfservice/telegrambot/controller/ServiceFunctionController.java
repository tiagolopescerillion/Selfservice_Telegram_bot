package com.selfservice.telegrambot.controller;

import com.selfservice.application.config.ApimanEndpointsProperties;
import com.selfservice.telegrambot.dto.QueryParamExportRequest;
import com.selfservice.telegrambot.dto.ServiceFunctionDescriptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
public class ServiceFunctionController {

    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final String LOCAL_CONFIG = "application-local.yml";
    private static final String EXAMPLE_CONFIG = "application-example.yml";

    private final ApimanEndpointsProperties apimanEndpoints;

    public ServiceFunctionController(ApimanEndpointsProperties apimanEndpoints) {
        this.apimanEndpoints = apimanEndpoints;
    }

    @GetMapping("/admin/service-functions")
    public Map<String, Object> listServiceFunctions() {
        List<ServiceFunctionDescriptor> endpoints = new ArrayList<>();

        addEndpoint(endpoints, "apiman.find-user.url", "Find user (APIMAN)",
                apimanEndpoints.getFindUserUrl(), List.of("FindUserService"),
                apimanEndpoints.getDefaultFindUserQueryParams(), apimanEndpoints.getFindUserQueryParams(),
                apimanEndpoints.getFindUserMethod(), "apiman.find-user.query-params");

        addEndpoint(endpoints, "apiman.product.url", "Product (APIMAN)",
                apimanEndpoints.getProductUrl(), List.of("ProductService"),
                apimanEndpoints.getDefaultProductQueryParams(), apimanEndpoints.getProductQueryParams(),
                apimanEndpoints.getProductMethod(), "apiman.product.query-params");

        addEndpoint(endpoints, "apiman.trouble-ticket.url", "Trouble ticket (APIMAN)",
                apimanEndpoints.getTroubleTicketUrl(), List.of("TroubleTicketService"),
                apimanEndpoints.getDefaultTroubleTicketQueryParams(), apimanEndpoints.getTroubleTicketQueryParams(),
                apimanEndpoints.getTroubleTicketMethod(), "apiman.trouble-ticket.query-params");

        return Map.of("endpoints", endpoints);
    }

    @PostMapping("/admin/service-functions/export")
    public ResponseEntity<byte[]> exportApplicationLocal(@RequestBody QueryParamExportRequest request) throws IOException {
        Map<String, Map<String, String>> updates = request == null ? Map.of() : request.updates();

        Optional<Path> configPath = resolveConfigPath();
        if (configPath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String updatedYaml = mergeQueryParams(configPath.get(), updates);
        byte[] payload = updatedYaml.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-yaml"));
        headers.setContentLength(payload.length);
        headers.setContentDispositionFormData("attachment", "application-local.yml");

        return ResponseEntity.ok()
                .headers(headers)
                .body(payload);
    }

    private void addEndpoint(List<ServiceFunctionDescriptor> endpoints, String key, String name, String url,
                             List<String> services, Map<String, String> defaultParams,
                             Map<String, String> configuredParams, org.springframework.http.HttpMethod method,
                             String queryParamKey) {
        boolean configured = url != null && !url.isBlank();
        endpoints.add(new ServiceFunctionDescriptor(key, name, configured ? url : null, configured, services,
                method == null ? "GET" : method.name(), defaultParams, configuredParams, queryParamKey));
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
        return Optional.empty();
    }

    private String mergeQueryParams(Path configPath, Map<String, Map<String, String>> updates) throws IOException {
        String content = Files.readString(configPath, StandardCharsets.UTF_8);
        Yaml yaml = new Yaml(buildDumperOptions());

        List<Object> documents = new ArrayList<>();
        yaml.loadAll(content).forEach(documents::add);

        List<Object> mutated = new ArrayList<>();
        for (Object doc : documents) {
            if (doc instanceof Map<?, ?> map) {
                mutated.add(applyUpdates(new LinkedHashMap<>((Map<String, Object>) map), updates));
            } else {
                mutated.add(doc);
            }
        }

        return yaml.dumpAll(mutated.iterator());
    }

    private DumperOptions buildDumperOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setWidth(80);
        return options;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyUpdates(Map<String, Object> document, Map<String, Map<String, String>> updates) {
        if (updates == null || updates.isEmpty()) {
            return document;
        }

        Map<String, Object> apiman = (Map<String, Object>) document.computeIfAbsent("apiman", key -> new LinkedHashMap<>());

        updates.forEach((qualifiedKey, params) -> {
            if (qualifiedKey == null || params == null) {
                return;
            }
            String[] parts = qualifiedKey.split("\\.");
            if (parts.length < 3) {
                return;
            }
            String endpointKey = parts[1];
            Map<String, Object> endpointConfig = (Map<String, Object>) apiman.computeIfAbsent(endpointKey,
                    k -> new LinkedHashMap<>());
            endpointConfig.put("query-params", coerceParameters(params));
        });

        return document;
    }

    private Map<String, Object> coerceParameters(Map<String, String> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        params.forEach((key, value) -> result.put(key, coerceScalar(value)));
        return result;
    }

    private Object coerceScalar(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(trimmed);
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            // not an int
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            // not a number
        }
        return trimmed;
    }
}
