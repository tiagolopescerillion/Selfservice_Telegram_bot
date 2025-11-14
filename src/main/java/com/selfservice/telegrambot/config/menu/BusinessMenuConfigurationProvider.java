package com.selfservice.telegrambot.config.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@Component
public class BusinessMenuConfigurationProvider {
    private static final Logger log = LoggerFactory.getLogger(BusinessMenuConfigurationProvider.class);
    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final Path DEFAULT_FILE = CONFIG_DIR.resolve("business-menu.default.json");
    private static final Path OVERRIDE_FILE = CONFIG_DIR.resolve("business-menu.override.json");
    private static final String PACKAGED_DEFAULT = "classpath:config/business-menu.default.json";

    private final List<BusinessMenuItem> menuItems;

    public BusinessMenuConfigurationProvider(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {

        String effectiveSource = OVERRIDE_FILE.toString();
        List<BusinessMenuItem> loadedMenu = tryLoadMenu(toFileResource(OVERRIDE_FILE), objectMapper, resourceLoader);

        if (loadedMenu.isEmpty()) {
            effectiveSource = DEFAULT_FILE.toString();
            log.info("Business menu override not found, falling back to {}", effectiveSource);
            loadedMenu = tryLoadMenu(toFileResource(DEFAULT_FILE), objectMapper, resourceLoader);
        }

        if (loadedMenu.isEmpty()) {
            effectiveSource = PACKAGED_DEFAULT;
            log.warn("Business menu default file missing, loading packaged fallback {}", PACKAGED_DEFAULT);
            loadedMenu = tryLoadMenu(PACKAGED_DEFAULT, objectMapper, resourceLoader);
        }

        if (loadedMenu.isEmpty()) {
            throw new IllegalStateException("Business menu configuration could not be loaded from CONFIGURATIONS");
        }

        this.menuItems = Collections.unmodifiableList(loadedMenu);
        log.info("Business menu configuration loaded from {}", effectiveSource);
    }

    public List<BusinessMenuItem> getMenuItems() {
        return menuItems;
    }

    private List<BusinessMenuItem> tryLoadMenu(
            String configPath,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(configPath);
        if (!resource.exists()) {
            log.warn("Business menu configuration not found at {}", configPath);
            return List.of();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            BusinessMenuConfiguration configuration = objectMapper.readValue(inputStream, BusinessMenuConfiguration.class);
            List<BusinessMenuItem> menu = configuration.sortedMenuItems();
            if (menu.isEmpty()) {
                log.warn("Business menu configuration at {} contains no menu entries", configPath);
            }
            return menu;
        } catch (IOException e) {
            log.error("Failed to load business menu configuration from {}: {}", configPath, e.getMessage());
            log.debug("Failed to load business menu configuration", e);
            return List.of();
        }
    }

    private String toFileResource(Path path) {
        return "file:" + path.toAbsolutePath();
    }
}
