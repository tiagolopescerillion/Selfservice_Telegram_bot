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

        BusinessMenuConfiguration selectedConfiguration = firstWithAnyMenu(overrideConfig, defaultConfig);
        BusinessMenuConfiguration preferredDefault = firstWithAnyMenu(defaultConfig, overrideConfig);
        List<BusinessMenuDefinition> loadedMenus = resolveMenus(overrideConfig, defaultConfig);
        LoginMenuDefinition loadedLoginMenu = resolveLoginMenu(overrideConfig, defaultConfig);

        if (selectedConfiguration == null || loadedMenus.isEmpty()) {
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
            copy.setParentId(definition.getParentId());
            copy.setItems(definition.sortedItems());
            mapped.put(copy.getId(), copy);
        }

        if (!mapped.containsKey(BusinessMenuDefinition.ROOT_MENU_ID)) {
            throw new IllegalStateException("Business menu configuration must include a root menu with id 'home'");
        }

        this.menusById = Collections.unmodifiableMap(mapped);
        this.loginMenuDefinition = loadedLoginMenu == null ? new LoginMenuDefinition() : loadedLoginMenu;
        this.effectiveConfiguration = snapshotConfiguration(
                selectedConfiguration,
                loadedMenus,
                this.loginMenuDefinition);
        List<BusinessMenuDefinition> defaultMenus = resolveMenus(preferredDefault, selectedConfiguration);
        LoginMenuDefinition defaultLoginMenu = resolveLoginMenu(preferredDefault, selectedConfiguration);
        this.defaultConfiguration = snapshotConfiguration(
                preferredDefault == null ? selectedConfiguration : preferredDefault,
                defaultMenus.isEmpty() ? loadedMenus : defaultMenus,
                defaultLoginMenu == null ? this.loginMenuDefinition : defaultLoginMenu);
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

    public BusinessMenuItem findMenuItemByCallback(String callbackData) {
        if (callbackData == null || callbackData.isBlank()) {
            return null;
        }
        return menusById.values().stream()
                .flatMap(def -> def.sortedItems().stream())
                .filter(item -> callbackData.equalsIgnoreCase(item.callbackData()))
                .findFirst()
                .orElseGet(() -> menusById.values().stream()
                        .flatMap(def -> def.sortedItems().stream())
                        .filter(item -> callbackData.equalsIgnoreCase(item.function()))
                        .findFirst()
                        .orElse(null));
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

    private BusinessMenuConfiguration firstWithAnyMenu(BusinessMenuConfiguration... candidates) {
        if (candidates == null) {
            return null;
        }
        for (BusinessMenuConfiguration candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (!candidate.normalizedMenus().isEmpty() || hasLoginMenuContent(candidate.normalizedLoginMenu())) {
                return candidate;
            }
        }
        return null;
    }

    private List<BusinessMenuDefinition> resolveMenus(
            BusinessMenuConfiguration primary,
            BusinessMenuConfiguration fallback) {
        if (primary != null && !primary.normalizedMenus().isEmpty()) {
            return primary.normalizedMenus();
        }
        if (fallback != null && !fallback.normalizedMenus().isEmpty()) {
            return fallback.normalizedMenus();
        }
        return List.of();
    }

    private LoginMenuDefinition resolveLoginMenu(
            BusinessMenuConfiguration primary,
            BusinessMenuConfiguration fallback) {
        if (primary != null && hasLoginMenuContent(primary.normalizedLoginMenu())) {
            return primary.normalizedLoginMenu();
        }
        if (fallback != null && hasLoginMenuContent(fallback.normalizedLoginMenu())) {
            return fallback.normalizedLoginMenu();
        }
        return null;
    }

    private boolean hasLoginMenuContent(LoginMenuDefinition loginMenu) {
        return loginMenu != null
                && ((loginMenu.getMenus() != null && !loginMenu.getMenus().isEmpty())
                || (loginMenu.getMenu() != null && !loginMenu.getMenu().isEmpty())
                || (loginMenu.getSettingsMenu() != null && !loginMenu.getSettingsMenu().isEmpty()));
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
                    copy.setParentId(menu.getParentId());
                    copy.setItems(menu.sortedItems());
                    return copy;
                })
                .toList();
    }

    private LoginMenuDefinition copyLoginMenu(LoginMenuDefinition menu) {
        LoginMenuDefinition copy = new LoginMenuDefinition();
        copy.setMenus(copyLoginMenus(menu == null ? List.of() : menu.getMenus()));
        copy.setMenu(menu == null ? List.of() : menu.normalizedMenu());
        copy.setSettingsMenu(menu == null ? List.of() : menu.normalizedSettingsMenu());
        return copy;
    }

    private List<BusinessMenuDefinition> copyLoginMenus(List<BusinessMenuDefinition> menus) {
        if (menus == null) {
            return List.of();
        }
        return menus.stream()
                .map(menu -> {
                    BusinessMenuDefinition copy = new BusinessMenuDefinition();
                    copy.setId(menu.getId());
                    copy.setName(menu.getName());
                    copy.setParentId(menu.getParentId());
                    copy.setItems(menu.sortedItems());
                    return copy;
                })
                .toList();
    }
}
