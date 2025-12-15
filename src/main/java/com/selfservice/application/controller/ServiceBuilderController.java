package com.selfservice.application.controller;

import com.selfservice.application.config.ApiRegistry;
import com.selfservice.application.config.ServiceCatalog;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/services")
@CrossOrigin(origins = "*")
public class ServiceBuilderController {

    private final ApiRegistry apiRegistry;
    private final ServiceCatalog serviceCatalog;

    public ServiceBuilderController(ApiRegistry apiRegistry, ServiceCatalog serviceCatalog) {
        this.apiRegistry = apiRegistry;
        this.serviceCatalog = serviceCatalog;
    }

    @GetMapping
    public Map<String, Object> listServices() {
        return Map.of(
                "apis", apiRegistry.getApis(),
                "services", serviceCatalog.getServices());
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportServices(@RequestBody(required = false) List<ServiceCatalog.ServiceDefinition> services) {
        List<ServiceCatalog.ServiceDefinition> payload = services == null || services.isEmpty()
                ? serviceCatalog.getServices()
                : services;
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("services", payload.stream()
                .map(service -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("Service Name", service.name());
                    entry.put("API-Name", service.apiName());
                    entry.put("Query Parameters", service.queryParameters());
                    entry.put("Response Template", service.responseTemplate().name());
                    entry.put("Output", service.output());
                    return entry;
                })
                .collect(Collectors.toList()));

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        String serialized = yaml.dump(document);

        byte[] bytes = serialized.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-yaml"));
        headers.setContentLength(bytes.length);
        headers.setContentDispositionFormData("attachment", "services-local.yml");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
