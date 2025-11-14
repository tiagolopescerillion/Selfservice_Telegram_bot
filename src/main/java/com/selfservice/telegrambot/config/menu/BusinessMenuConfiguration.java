package com.selfservice.telegrambot.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Comparator;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessMenuConfiguration {
    private int version;
    private String generatedAt;
    private List<BusinessMenuDefinition> menus;
    private List<BusinessMenuItem> menu;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<BusinessMenuDefinition> getMenus() {
        return menus;
    }

    public void setMenus(List<BusinessMenuDefinition> menus) {
        this.menus = menus;
    }

    public List<BusinessMenuItem> getMenu() {
        return menu;
    }

    public void setMenu(List<BusinessMenuItem> menu) {
        this.menu = menu;
    }

    public List<BusinessMenuDefinition> normalizedMenus() {
        if (menus != null && !menus.isEmpty()) {
            return menus;
        }
        if (menu == null || menu.isEmpty()) {
            return List.of();
        }
        BusinessMenuDefinition root = new BusinessMenuDefinition();
        root.setId(BusinessMenuDefinition.ROOT_MENU_ID);
        root.setName("Home");
        root.setItems(menu.stream()
                .sorted(Comparator.comparingInt(BusinessMenuItem::order))
                .toList());
        return List.of(root);
    }
}
