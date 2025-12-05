package com.selfservice.application.controller;

import com.selfservice.application.config.ApimanEndpointsProperties;
import com.selfservice.application.config.ServiceFunctionProperties;
import com.selfservice.application.dto.QueryParamExportRequest;
import com.selfservice.application.dto.ServiceFunctionConfig;
import com.selfservice.application.dto.ServiceFunctionDescriptor;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class ServiceFunctionController {

    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final String LOCAL_CONFIG = "application-local.yml";
    private static final String EXAMPLE_CONFIG = "application-example.yml";

    private final ApimanEndpointsProperties apimanEndpoints;
    private final ServiceFunctionProperties serviceFunctionProperties;

    public ServiceFunctionController(ApimanEndpointsProperties apimanEndpoints,
                                     ServiceFunctionProperties serviceFunctionProperties) {
        this.apimanEndpoints = apimanEndpoints;
        this.serviceFunctionProperties = serviceFunctionProperties;
    }

    @GetMapping("/admin/service-functions")
    public Map<String, Object> listServiceFunctions() {
        List<ServiceFunctionDescriptor> endpoints = new ArrayList<>();

        addEndpoint(endpoints, "apiman.find-user.url", "Find user (APIMAN)",
                apimanEndpoints.getFindUserUrl(), List.of("FindUserService"),
                apimanEndpoints.getDefaultFindUserQueryParams(), apimanEndpoints.getFindUserQueryParams(),
                apimanEndpoints.getFindUserMethod(), "apiman.find-user.query-params", null, null,
                "find-user", false);

        addEndpoint(endpoints, "apiman.product.url", "Product (APIMAN)",
                apimanEndpoints.getProductUrl(), List.of("ProductService"),
                apimanEndpoints.getDefaultProductQueryParams(), apimanEndpoints.getProductQueryParams(),
                apimanEndpoints.getProductMethod(), "apiman.product.query-params", "billingAccount.id", null,
                "product", false);

        addEndpoint(endpoints, "apiman.trouble-ticket.url", "Trouble ticket (APIMAN)",
                apimanEndpoints.getTroubleTicketUrl(), List.of("TroubleTicketService"),
                apimanEndpoints.getDefaultTroubleTicketQueryParams(), apimanEndpoints.getTroubleTicketQueryParams(),
                apimanEndpoints.getTroubleTicketMethod(), "apiman.trouble-ticket.query-params",
                "billingAccount.id", "relatedEntity.product.id", "trouble-ticket", false);

        addEndpoint(endpoints, "apiman.bill.url", "Bill history (APIMAN)",
                apimanEndpoints.getBillUrl(), List.of("InvoiceService"),
                apimanEndpoints.getDefaultBillQueryParams(), apimanEndpoints.getBillQueryParams(),
                apimanEndpoints.getBillMethod(), "apiman.bill.query-params",
                "billingAccount.id", null, "bill", false);

        Map<String, ServiceFunctionDescriptor> baseByKey = endpoints.stream()
                .collect(Collectors.toMap(ServiceFunctionDescriptor::endpointKey, Function.identity()));

        for (ServiceFunctionProperties.Definition definition : serviceFunctionProperties.getEntries()) {
            String endpointKey = definition.getEndpoint();
            if (!baseByKey.containsKey(endpointKey)) {
                continue;
            }
            ServiceFunctionDescriptor base = baseByKey.get(endpointKey);
            boolean accountContext = definition.isAccountContext();
            boolean serviceContext = definition.isServiceContext();
            Map<String, String> defaultParams = base.defaultQueryParams();
            Map<String, String> configured = new LinkedHashMap<>(definition.getQueryParams().isEmpty()
                    ? base.configuredQueryParams()
                    : definition.getQueryParams());
            if (base.accountContextParam() != null) {
                if (accountContext) {
                    configured.putIfAbsent(base.accountContextParam(), "");
                } else {
                    configured.remove(base.accountContextParam());
                }
            }
            if (base.serviceContextParam() != null) {
                if (serviceContext) {
                    configured.putIfAbsent(base.serviceContextParam(), "");
                } else {
                    configured.remove(base.serviceContextParam());
                }
            }
            addEndpoint(endpoints, "service-functions." + sanitizeKey(definition.getName()), definition.getName(),
                    base.url(), base.services(), defaultParams, configured,
                    org.springframework.http.HttpMethod.valueOf(base.method()),
                    "service-functions." + sanitizeKey(definition.getName()) + ".query-params",
                    base.accountContextParam(),
                    base.serviceContextParam(),
                    endpointKey, true);
        }

        return Map.of(
                "endpoints", endpoints,
                "availableEndpoints", endpoints.stream()
                        .filter(descriptor -> !descriptor.custom())
                        .collect(Collectors.toList())
        );
    }

    @PostMapping("/admin/service-functions/export")
    public ResponseEntity<byte[]> exportApplicationLocal(@RequestBody QueryParamExportRequest request) throws IOException {
        Map<String, Map<String, String>> updates = request == null ? Map.of() : request.updates();
        List<ServiceFunctionConfig> customFunctions = request == null || request.serviceFunctions() == null
                ? List.of()
                : request.serviceFunctions();

        Optional<Path> configPath = resolveConfigPath();
        if (configPath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String updatedYaml = mergeQueryParams(configPath.get(), updates, customFunctions);
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
                             String queryParamKey, String accountContextParam, String serviceContextParam,
                             String endpointKey, boolean custom) {
        boolean configured = url != null && !url.isBlank();
        Map<String, String> safeDefaults = defaultParams == null ? Map.of() : Map.copyOf(defaultParams);
        Map<String, String> safeConfigured = configuredParams == null ? Map.of() : Map.copyOf(configuredParams);
        boolean accountContextEnabled = accountContextParam != null
                && (safeConfigured.containsKey(accountContextParam) || safeDefaults.containsKey(accountContextParam)
                        || safeConfigured.isEmpty());
        boolean serviceContextEnabled = serviceContextParam != null && safeConfigured.containsKey(serviceContextParam);
        endpoints.add(new ServiceFunctionDescriptor(key, name, configured ? url : null, configured, services,
                method == null ? "GET" : method.name(), safeDefaults, safeConfigured, queryParamKey,
                accountContextParam, accountContextEnabled, serviceContextParam, serviceContextEnabled, endpointKey,
                custom));
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

    private String mergeQueryParams(Path configPath, Map<String, Map<String, String>> updates,
                                    List<ServiceFunctionConfig> customFunctions) throws IOException {
        String content = Files.readString(configPath, StandardCharsets.UTF_8);
        Yaml yaml = new Yaml(buildDumperOptions());

        List<Object> documents = new ArrayList<>();
        yaml.loadAll(content).forEach(documents::add);

        List<Object> mutated = new ArrayList<>();
        for (Object doc : documents) {
            if (doc instanceof Map<?, ?> map) {
                mutated.add(applyUpdates(new LinkedHashMap<>((Map<String, Object>) map), updates, customFunctions));
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
    private Map<String, Object> applyUpdates(Map<String, Object> document, Map<String, Map<String, String>> updates,
                                             List<ServiceFunctionConfig> customFunctions) {
        Map<String, Object> apiman = (Map<String, Object>) document.computeIfAbsent("apiman", key -> new LinkedHashMap<>());

        if (updates != null) {
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
        }

        if (customFunctions != null && !customFunctions.isEmpty()) {
            List<Map<String, Object>> exported = new ArrayList<>();
            for (ServiceFunctionConfig customFunction : customFunctions) {
                if (customFunction == null) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", customFunction.name());
                entry.put("endpoint", customFunction.endpointKey());
                entry.put("query-params", coerceParameters(customFunction.queryParams()));
                entry.put("account-context", customFunction.accountContext());
                entry.put("service-context", customFunction.serviceContext());
                exported.add(entry);
            }
            document.put("service-functions", Map.of("entries", exported));
        }

        return document;
    }

    private String sanitizeKey(String name) {
        if (name == null) {
            return "custom";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
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
