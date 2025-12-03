package com.selfservice.application.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginMenuDefinition {
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
        return normalize(menu, defaultMenuItems());
    }

    public List<LoginMenuItem> normalizedSettingsMenu() {
        List<LoginMenuItem> treeSettingsItems = normalizedTreeSettingsMenu();
        if (!treeSettingsItems.isEmpty()) {
            return treeSettingsItems;
        }
        return normalize(settingsMenu, defaultSettingsItems());
    }

    private List<LoginMenuItem> normalizedTreeMenu() {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        BusinessMenuDefinition root = menus.stream()
                .filter(menu -> menu.getId() != null && menu.getId().equalsIgnoreCase("login-home"))
                .findFirst()
                .orElse(menus.get(0));
        return convertActions(root);
    }

    private List<LoginMenuItem> normalizedTreeSettingsMenu() {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        BusinessMenuDefinition root = menus.stream()
                .filter(menu -> menu.getId() != null && menu.getId().equalsIgnoreCase("login-home"))
                .findFirst()
                .orElse(menus.get(0));
        String settingsId = root.sortedItems().stream()
                .filter(BusinessMenuItem::isSubMenu)
                .map(BusinessMenuItem::submenuId)
                .findFirst()
                .orElse(null);
        if (settingsId == null) {
            settingsId = menus.stream()
                    .filter(menu -> root.getId().equals(menu.getParentId()))
                    .map(BusinessMenuDefinition::getId)
                    .findFirst()
                    .orElse(null);
        }
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
                .filter(item -> item.resolvedFunction() != null)
                .toList();
    }

    private List<LoginMenuItem> normalize(List<LoginMenuItem> input, List<LoginMenuItem> defaults) {
        List<LoginMenuItem> candidates = (input == null || input.isEmpty()) ? defaults : input;
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.resolvedFunction() != null)
                .sorted(Comparator.comparingInt(LoginMenuItem::getOrder))
                .map(LoginMenuDefinition::copy)
                .toList();
    }

    private static List<LoginMenuItem> defaultMenuItems() {
        return List.of(
                item(1, "Self-service login", LoginMenuFunction.DIGITAL_LOGIN,
                        "ButtonSelfServiceLogin", "SELF_SERVICE_LOGIN"),
                item(2, "Direct login", LoginMenuFunction.CRM_LOGIN,
                        "ButtonDirectLogin", "DIRECT_LOGIN"),
                item(3, "Settings", LoginMenuFunction.SETTINGS,
                        "ButtonSettings", "SETTINGS_MENU")
        );
    }

    private static List<LoginMenuItem> defaultSettingsItems() {
        return List.of(
                item(1, "Consent management", LoginMenuFunction.OPT_IN,
                        "ButtonOptIn", "OPT_IN"),
                item(2, "Language settings", LoginMenuFunction.CHANGE_LANGUAGE,
                        "ButtonChangeLanguage", "CHANGE_LANGUAGE"),
                item(3, "Back to menu", LoginMenuFunction.MENU,
                        "ButtonMenu", "MENU")
        );
    }

    private static LoginMenuItem item(int order, String label, LoginMenuFunction function, String translationKey,
            String callbackData) {
        LoginMenuItem item = new LoginMenuItem();
        item.setOrder(order);
        item.setLabel(label);
        item.setFunction(function);
        item.setTranslationKey(translationKey);
        item.setCallbackData(callbackData);
        return item;
    }

    private static LoginMenuItem copy(LoginMenuItem source) {
        LoginMenuItem item = new LoginMenuItem();
        item.setOrder(source.getOrder());
        item.setLabel(source.getLabel());
        item.setFunction(source.resolvedFunction());
        item.setTranslationKey(source.getTranslationKey());
        item.setCallbackData(source.getCallbackData());
        return item;
    }

    private static LoginMenuItem toLoginMenuItem(BusinessMenuItem source) {
        LoginMenuItem item = new LoginMenuItem();
        item.setOrder(source.order());
        item.setLabel(source.label());
        item.setFunction(LoginMenuFunction.fromString(source.function()));
        item.setTranslationKey(source.translationKey());
        item.setCallbackData(source.callbackData());
        return item;
    }

    public Stream<LoginMenuItem> allItems() {
        return Stream.concat(normalizedMenu().stream(), normalizedSettingsMenu().stream());
    }
}
