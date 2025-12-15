package com.selfservice.application.controller;

import com.selfservice.application.config.menu.BusinessMenuConfiguration;
import com.selfservice.application.config.menu.BusinessMenuConfigurationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/menu-config")
@CrossOrigin
public class MenuConfigurationController {

    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final Path OVERRIDE_FILE = CONFIG_DIR.resolve("IM-menus.override.json");

    private final BusinessMenuConfigurationProvider configurationProvider;
    private final ObjectMapper objectMapper;

    public MenuConfigurationController(BusinessMenuConfigurationProvider configurationProvider, ObjectMapper objectMapper) {
        this.configurationProvider = configurationProvider;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public BusinessMenuConfiguration getMenuConfiguration() {
        return configurationProvider.getEffectiveConfiguration();
    }

    @GetMapping("/default")
    public BusinessMenuConfiguration getDefaultMenuConfiguration() {
        return configurationProvider.getDefaultConfiguration();
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveMenuConfiguration(@RequestBody BusinessMenuConfiguration configuration) {
        try {
            Files.createDirectories(CONFIG_DIR);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(OVERRIDE_FILE.toFile(), configuration);
            configurationProvider.reload();
            return ResponseEntity.ok(Map.of(
                    "file", OVERRIDE_FILE.getFileName().toString(),
                    "configuration", configurationProvider.getEffectiveConfiguration()
            ));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "reason", "Unable to save menu configuration: " + ex.getMessage()
            ));
        }
    }
}

