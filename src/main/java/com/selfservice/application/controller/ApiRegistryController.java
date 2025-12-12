package com.selfservice.application.controller;

import com.selfservice.application.config.ApiRegistry;
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

@RestController
@RequestMapping("/admin/apis")
@CrossOrigin(origins = "*")
public class ApiRegistryController {

    private final ApiRegistry apiRegistry;

    public ApiRegistryController(ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
    }

    @GetMapping
    public Map<String, Object> listApis() {
        return Map.of("apis", apiRegistry.getApis());
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportApis(@RequestBody(required = false) List<ApiRegistry.ApiDefinition> apis) {
        List<ApiRegistry.ApiDefinition> payload = apis == null || apis.isEmpty() ? apiRegistry.getApis() : apis;
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

        byte[] bytes = serialized.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-yaml"));
        headers.setContentLength(bytes.length);
        headers.setContentDispositionFormData("attachment", "API-list-local.yml");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
