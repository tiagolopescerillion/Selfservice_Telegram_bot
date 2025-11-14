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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BusinessMenuConfigurationProvider {
    private static final Logger log = LoggerFactory.getLogger(BusinessMenuConfigurationProvider.class);
    private static final Path CONFIG_DIR = Paths.get("CONFIGURATIONS");
    private static final Path DEFAULT_FILE = CONFIG_DIR.resolve("business-menu.default.json");
    private static final Path OVERRIDE_FILE = CONFIG_DIR.resolve("business-menu.override.json");
    private static final String PACKAGED_DEFAULT = "classpath:config/business-menu.default.json";

    private final Map<String, BusinessMenuDefinition> menusById;

    public BusinessMenuConfigurationProvider(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {

        String effectiveSource = OVERRIDE_FILE.toString();
        List<BusinessMenuDefinition> loadedMenus = tryLoadMenus(toFileResource(OVERRIDE_FILE), objectMapper, resourceLoader);

        if (loadedMenus.isEmpty()) {
            effectiveSource = DEFAULT_FILE.toString();
            log.info("Business menu override not found, falling back to {}", effectiveSource);
            loadedMenus = tryLoadMenus(toFileResource(DEFAULT_FILE), objectMapper, resourceLoader);
        }

        if (loadedMenus.isEmpty()) {
            effectiveSource = PACKAGED_DEFAULT;
            log.warn("Business menu default file missing, loading packaged fallback {}", PACKAGED_DEFAULT);
            loadedMenus = tryLoadMenus(PACKAGED_DEFAULT, objectMapper, resourceLoader);
        }

        if (loadedMenus.isEmpty()) {
            throw new IllegalStateException("Business menu configuration could not be loaded from CONFIGURATIONS");
        }

        Map<String, BusinessMenuDefinition> mapped = new LinkedHashMap<>();
        for (BusinessMenuDefinition definition : loadedMenus) {
            if (definition == null || definition.getId() == null || definition.getId().isBlank()) {
                log.warn("Skipping business menu without an id");
                continue;
            }
            BusinessMenuDefinition copy = new BusinessMenuDefinition();
            copy.setId(definition.getId());
            String resolvedName = definition.getName();
            if (resolvedName == null || resolvedName.isBlank()) {
                resolvedName = BusinessMenuDefinition.ROOT_MENU_ID.equals(definition.getId()) ? "Home" : definition.getId();
            }
            copy.setName(resolvedName);
            copy.setItems(definition.sortedItems());
            mapped.put(copy.getId(), copy);
        }

        if (!mapped.containsKey(BusinessMenuDefinition.ROOT_MENU_ID)) {
            throw new IllegalStateException("Business menu configuration must include a root menu with id 'home'");
        }

        this.menusById = Collections.unmodifiableMap(mapped);
        log.info("Business menu configuration loaded from {} with {} menus", effectiveSource, menusById.size());
    }

    public String getRootMenuId() {
        return BusinessMenuDefinition.ROOT_MENU_ID;
    }

    public boolean menuExists(String menuId) {
        return menuId != null && menusById.containsKey(menuId);
    }

    public List<BusinessMenuItem> getMenuItems(String menuId) {
        BusinessMenuDefinition definition = menusById.getOrDefault(menuId, menusById.get(getRootMenuId()));
        if (definition == null) {
            return List.of();
        }
        return definition.sortedItems();
    }

    private List<BusinessMenuDefinition> tryLoadMenus(
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
            List<BusinessMenuDefinition> menus = configuration.normalizedMenus();
            if (menus.isEmpty()) {
                log.warn("Business menu configuration at {} contains no menu entries", configPath);
            }
            return menus;
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
