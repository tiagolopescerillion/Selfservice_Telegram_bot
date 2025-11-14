package com.selfservice.telegrambot.config.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class BusinessMenuConfigurationProvider {
    private static final Logger log = LoggerFactory.getLogger(BusinessMenuConfigurationProvider.class);
    private static final String DEFAULT_RESOURCE = "classpath:config/business-menu.default.json";

    private final List<BusinessMenuItem> menuItems;

    public BusinessMenuConfigurationProvider(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${business-menu.config-path:classpath:config/business-menu.default.json}") String configPath) {

        List<BusinessMenuItem> loadedMenu = tryLoadMenu(configPath, objectMapper, resourceLoader);
        String effectiveSource = configPath;

        if (loadedMenu.isEmpty() && !Objects.equals(configPath, DEFAULT_RESOURCE)) {
            log.warn("Falling back to default business menu configuration at {}", DEFAULT_RESOURCE);
            loadedMenu = tryLoadMenu(DEFAULT_RESOURCE, objectMapper, resourceLoader);
            effectiveSource = DEFAULT_RESOURCE;
        }

        if (loadedMenu.isEmpty()) {
            throw new IllegalStateException("Business menu configuration could not be loaded from " + configPath);
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
}
