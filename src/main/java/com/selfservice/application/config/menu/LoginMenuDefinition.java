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
                        LoginMenuItem settings = new LoginMenuItem();
                        settings.setOrder(item.order());
                        settings.setLabel(item.label() == null || item.label().isBlank() ? "Settings" : item.label());
                        settings.setFunction(LoginMenuFunction.SETTINGS.name());
                        settings.setTranslationKey(item.translationKey() == null || item.translationKey().isBlank()
                                ? "ButtonSettings"
                                : item.translationKey());
                        settings.setCallbackData(item.callbackData() == null || item.callbackData().isBlank()
                                ? "SETTINGS_MENU"
                                : item.callbackData());
                        return settings;
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
        LoginMenuItem item = new LoginMenuItem();
        item.setOrder(source.getOrder());
        item.setLabel(source.getLabel());
        item.setFunction(source.getFunction());
        item.setTranslationKey(source.getTranslationKey());
        item.setCallbackData(source.getCallbackData());
        return item;
    }

    public static LoginMenuItem toLoginMenuItem(BusinessMenuItem source) {
        LoginMenuItem item = new LoginMenuItem();
        item.setOrder(source.order());
        if (source.isSubMenu()) {
            item.setLabel(source.label() == null || source.label().isBlank() ? "Settings" : source.label());
            item.setFunction(SETTINGS.name());
            item.setTranslationKey(source.translationKey() == null || source.translationKey().isBlank()
                    ? "ButtonSettings"
                    : source.translationKey());
            item.setCallbackData(source.callbackData() == null || source.callbackData().isBlank()
                    ? "SETTINGS_MENU"
                    : source.callbackData());
            return item;
        }
        item.setLabel(source.label());
        item.setFunction(source.function());
        item.setTranslationKey(source.translationKey());
        item.setCallbackData(source.callbackData());
        return item;
    }

    public Stream<LoginMenuItem> allItems() {
        return Stream.concat(normalizedMenu().stream(), normalizedSettingsMenu().stream());
    }
}
