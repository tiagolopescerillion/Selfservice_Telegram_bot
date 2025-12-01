package com.selfservice.application.config.menu;

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
    private static final Path DEFAULT_FILE = CONFIG_DIR.resolve("IM-menus.default.json");
    private static final Path OVERRIDE_FILE = CONFIG_DIR.resolve("IM-menus.override.json");
    private static final String PACKAGED_DEFAULT = "classpath:config/IM-menus.default.json";

    private final Map<String, BusinessMenuDefinition> menusById;
    private final LoginMenuDefinition loginMenuDefinition;
    private final BusinessMenuConfiguration effectiveConfiguration;
    private final BusinessMenuConfiguration defaultConfiguration;

    public BusinessMenuConfigurationProvider(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {

        BusinessMenuConfiguration overrideConfig = tryLoadConfiguration(
                toFileResource(OVERRIDE_FILE), objectMapper, resourceLoader);
        BusinessMenuConfiguration defaultConfig = tryLoadConfiguration(
                toFileResource(DEFAULT_FILE), objectMapper, resourceLoader);
        BusinessMenuConfiguration packagedConfig = tryLoadConfiguration(
                PACKAGED_DEFAULT, objectMapper, resourceLoader);

        BusinessMenuConfiguration selectedConfiguration = firstWithMenus(overrideConfig, defaultConfig, packagedConfig);
        BusinessMenuConfiguration preferredDefault = firstWithMenus(defaultConfig, packagedConfig, overrideConfig);

        if (selectedConfiguration == null || selectedConfiguration.normalizedMenus().isEmpty()) {
            throw new IllegalStateException("Business menu configuration could not be loaded from CONFIGURATIONS");
        }

        List<BusinessMenuDefinition> loadedMenus = selectedConfiguration.normalizedMenus();
        LoginMenuDefinition loadedLoginMenu = selectedConfiguration.normalizedLoginMenu();

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
        this.loginMenuDefinition = loadedLoginMenu == null ? new LoginMenuDefinition() : loadedLoginMenu;
        this.effectiveConfiguration = snapshotConfiguration(selectedConfiguration, loadedMenus, this.loginMenuDefinition);
        this.defaultConfiguration = snapshotConfiguration(
                preferredDefault == null ? selectedConfiguration : preferredDefault,
                preferredDefault == null ? loadedMenus : preferredDefault.normalizedMenus(),
                preferredDefault == null ? this.loginMenuDefinition : preferredDefault.normalizedLoginMenu());
        log.info("Business menu configuration loaded with {} menus", menusById.size());
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

    public LoginMenuDefinition getLoginMenuDefinition() {
        return loginMenuDefinition;
    }

    public List<LoginMenuItem> getLoginMenuItems() {
        return loginMenuDefinition.normalizedMenu();
    }

    public List<LoginMenuItem> getLoginSettingsMenuItems() {
        return loginMenuDefinition.normalizedSettingsMenu();
    }

    public LoginMenuItem findLoginMenuItemByCallback(String callbackData) {
        if (callbackData == null || callbackData.isBlank()) {
            return null;
        }
        return loginMenuDefinition.allItems()
                .filter(item -> callbackData.equalsIgnoreCase(item.getCallbackData()))
                .findFirst()
                .orElse(null);
    }

    public BusinessMenuConfiguration getEffectiveConfiguration() {
        return copyConfiguration(effectiveConfiguration);
    }

    public BusinessMenuConfiguration getDefaultConfiguration() {
        return copyConfiguration(defaultConfiguration);
    }

    private BusinessMenuConfiguration tryLoadConfiguration(
            String configPath,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(configPath);
        if (!resource.exists()) {
            log.warn("Business menu configuration not found at {}", configPath);
            return new BusinessMenuConfiguration();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            BusinessMenuConfiguration configuration = objectMapper.readValue(inputStream, BusinessMenuConfiguration.class);
            if (configuration.normalizedMenus().isEmpty()) {
                log.warn("Business menu configuration at {} contains no menu entries", configPath);
            }
            return configuration;
        } catch (IOException e) {
            log.error("Failed to load business menu configuration from {}: {}", configPath, e.getMessage());
            log.debug("Failed to load business menu configuration", e);
            return new BusinessMenuConfiguration();
        }
    }

    private String toFileResource(Path path) {
        return "file:" + path.toAbsolutePath();
    }

    private BusinessMenuConfiguration firstWithMenus(BusinessMenuConfiguration... candidates) {
        if (candidates == null) {
            return null;
        }
        for (BusinessMenuConfiguration candidate : candidates) {
            if (candidate != null && !candidate.normalizedMenus().isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private BusinessMenuConfiguration snapshotConfiguration(
            BusinessMenuConfiguration source,
            List<BusinessMenuDefinition> menus,
            LoginMenuDefinition loginMenu) {
        BusinessMenuConfiguration snapshot = new BusinessMenuConfiguration();
        snapshot.setVersion(source == null ? 0 : source.getVersion());
        snapshot.setGeneratedAt(source == null ? null : source.getGeneratedAt());
        snapshot.setMenus(copyMenus(menus));
        snapshot.setLoginMenu(copyLoginMenu(loginMenu));
        return snapshot;
    }

    private BusinessMenuConfiguration copyConfiguration(BusinessMenuConfiguration source) {
        if (source == null) {
            return snapshotConfiguration(null, List.of(), new LoginMenuDefinition());
        }
        return snapshotConfiguration(source, source.normalizedMenus(), source.normalizedLoginMenu());
    }

    private List<BusinessMenuDefinition> copyMenus(List<BusinessMenuDefinition> menus) {
        if (menus == null) {
            return List.of();
        }
        return menus.stream()
                .map(menu -> {
                    BusinessMenuDefinition copy = new BusinessMenuDefinition();
                    copy.setId(menu.getId());
                    copy.setName(menu.getName());
                    copy.setItems(menu.sortedItems());
                    return copy;
                })
                .toList();
    }

    private LoginMenuDefinition copyLoginMenu(LoginMenuDefinition menu) {
        LoginMenuDefinition copy = new LoginMenuDefinition();
        copy.setMenu(menu == null ? List.of() : menu.normalizedMenu());
        copy.setSettingsMenu(menu == null ? List.of() : menu.normalizedSettingsMenu());
        return copy;
    }
}
