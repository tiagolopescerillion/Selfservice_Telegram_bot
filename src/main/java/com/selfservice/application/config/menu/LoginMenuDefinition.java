package com.selfservice.application.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.selfservice.application.config.menu.LoginMenuFunction;

import static com.selfservice.application.config.menu.LoginMenuFunction.SETTINGS;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginMenuDefinition {
    public static final String ROOT_MENU_ID = "login-home";

    private List<BusinessMenuDefinition> menus;
    private List<LoginMenuItem> menu;
    private List<LoginMenuItem> settingsMenu;

    public List<BusinessMenuDefinition> getMenus() {
        return menus;
    }

    public void setMenus(List<BusinessMenuDefinition> menus) {
        this.menus = menus;
    }

    public List<LoginMenuItem> getMenu() {
        return menu;
    }

    public void setMenu(List<LoginMenuItem> menu) {
        this.menu = menu;
    }

    public List<LoginMenuItem> getSettingsMenu() {
        return settingsMenu;
    }

    public void setSettingsMenu(List<LoginMenuItem> settingsMenu) {
        this.settingsMenu = settingsMenu;
    }

    public List<LoginMenuItem> normalizedMenu() {
        List<LoginMenuItem> treeMenuItems = normalizedTreeMenu();
        if (!treeMenuItems.isEmpty()) {
            return treeMenuItems;
        }
        return normalize(menu, List.of());
    }

    public List<LoginMenuItem> normalizedSettingsMenu() {
        List<LoginMenuItem> treeSettingsItems = normalizedTreeSettingsMenu();
        if (!treeSettingsItems.isEmpty()) {
            return treeSettingsItems;
        }
        return normalize(settingsMenu, List.of());
    }

    private List<LoginMenuItem> normalizedTreeMenu() {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        BusinessMenuDefinition root = menus.stream()
                .filter(menu -> menu.getId() != null && menu.getId().equalsIgnoreCase("login-home"))
                .findFirst()
                .orElse(menus.get(0));
        return convertMenuEntries(root);
    }

    private List<LoginMenuItem> normalizedTreeSettingsMenu() {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        BusinessMenuDefinition root = menus.stream()
                .filter(menu -> menu.getId() != null && menu.getId().equalsIgnoreCase("login-home"))
                .findFirst()
                .orElse(menus.get(0));
        String settingsId = Stream.concat(
                        root.sortedItems().stream()
                                .filter(BusinessMenuItem::isSubMenu)
                                .map(BusinessMenuItem::submenuId),
                        menus.stream()
                                .filter(menu -> root.getId().equals(menu.getParentId()))
                                .map(BusinessMenuDefinition::getId))
                .findFirst()
                .orElse(null);
        if (settingsId == null) {
            return List.of();
        }
        return menus.stream()
                .filter(menu -> settingsId.equals(menu.getId()))
                .findFirst()
                .map(this::convertActions)
                .orElse(List.of());
    }

    private List<LoginMenuItem> convertActions(BusinessMenuDefinition menuDefinition) {
        if (menuDefinition == null) {
            return List.of();
        }
        return menuDefinition.sortedItems().stream()
                .filter(BusinessMenuItem::isAction)
                .map(LoginMenuDefinition::toLoginMenuItem)
                .toList();
    }

    private List<LoginMenuItem> convertMenuEntries(BusinessMenuDefinition menuDefinition) {
        if (menuDefinition == null) {
            return List.of();
        }
        return menuDefinition.sortedItems().stream()
                .map(item -> {
                    if (item.isSubMenu()) {
                        String label = item.label() == null || item.label().isBlank() ? "Settings" : item.label();
                        String translation = item.translationKey() == null || item.translationKey().isBlank()
                                ? "ButtonSettings"
                                : item.translationKey();
                        String callback = item.callbackData() == null || item.callbackData().isBlank()
                                ? "SETTINGS_MENU"
                                : item.callbackData();
                        return new LoginMenuItem(
                                item.type() == null || item.type().isBlank() ? "submenu" : item.type(),
                                item.order(),
                                label,
                                LoginMenuFunction.SETTINGS.name(),
                                callback,
                                translation,
                                item.submenuId(),
                                item.weblink(),
                                item.url(),
                                item.authenticated(),
                                item.context(),
                                item.useTranslation(),
                                item.accountContextEnabled(),
                                item.accountContextKey(),
                                item.accountContextLabel(),
                                item.serviceContextEnabled(),
                                item.serviceContextKey(),
                                item.serviceContextLabel(),
                                item.menuContextEnabled(),
                                item.menuContextKey(),
                                item.menuContextLabel());
                    }
                    if (item.isAction()) {
                        return toLoginMenuItem(item);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<LoginMenuItem> normalize(List<LoginMenuItem> input, List<LoginMenuItem> defaults) {
        List<LoginMenuItem> candidates = (input == null || input.isEmpty()) ? defaults : input;
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(LoginMenuItem::getOrder))
                .map(LoginMenuDefinition::copy)
                .toList();
    }

    private static LoginMenuItem copy(LoginMenuItem source) {
        return new LoginMenuItem(
                source.type(),
                source.order(),
                source.label(),
                source.function(),
                source.callbackData(),
                source.translationKey(),
                source.submenuId(),
                source.weblink(),
                source.url(),
                source.authenticated(),
                source.context(),
                source.useTranslation(),
                source.accountContextEnabled(),
                source.accountContextKey(),
                source.accountContextLabel(),
                source.serviceContextEnabled(),
                source.serviceContextKey(),
                source.serviceContextLabel(),
                source.menuContextEnabled(),
                source.menuContextKey(),
                source.menuContextLabel());
    }

    public static LoginMenuItem toLoginMenuItem(BusinessMenuItem source) {
        if (source.isSubMenu()) {
            String label = source.label() == null || source.label().isBlank() ? "Settings" : source.label();
            String translation = source.translationKey() == null || source.translationKey().isBlank()
                    ? "ButtonSettings"
                    : source.translationKey();
            String callback = source.callbackData() == null || source.callbackData().isBlank()
                    ? "SETTINGS_MENU"
                    : source.callbackData();
            return new LoginMenuItem(
                    source.type() == null || source.type().isBlank() ? "submenu" : source.type(),
                    source.order(),
                    label,
                    SETTINGS.name(),
                    callback,
                    translation,
                    source.submenuId(),
                    source.weblink(),
                    source.url(),
                    source.authenticated(),
                    source.context(),
                    source.useTranslation(),
                    source.accountContextEnabled(),
                    source.accountContextKey(),
                    source.accountContextLabel(),
                    source.serviceContextEnabled(),
                    source.serviceContextKey(),
                    source.serviceContextLabel(),
                    source.menuContextEnabled(),
                    source.menuContextKey(),
                    source.menuContextLabel());
        }
        return new LoginMenuItem(
                source.type(),
                source.order(),
                source.label(),
                source.function(),
                source.callbackData(),
                source.translationKey(),
                source.submenuId(),
                source.weblink(),
                source.url(),
                source.authenticated(),
                source.context(),
                source.useTranslation(),
                source.accountContextEnabled(),
                source.accountContextKey(),
                source.accountContextLabel(),
                source.serviceContextEnabled(),
                source.serviceContextKey(),
                source.serviceContextLabel(),
                source.menuContextEnabled(),
                source.menuContextKey(),
                source.menuContextLabel());
    }

    public Stream<LoginMenuItem> allItems() {
        return Stream.concat(normalizedMenu().stream(), normalizedSettingsMenu().stream());
    }
}
