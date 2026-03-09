package com.selfservice.application.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessMenuConfiguration {
    private int version;
    private String generatedAt;
    private List<BusinessMenuDefinition> menus;
    private List<BusinessMenuItem> menu;
    private LoginMenuDefinition loginMenu;
    private Map<String, MenuOutputConfiguration> productFeatureMenus;

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

    public LoginMenuDefinition getLoginMenu() {
        return loginMenu;
    }

    public void setLoginMenu(LoginMenuDefinition loginMenu) {
        this.loginMenu = loginMenu;
    }


    public Map<String, MenuOutputConfiguration> getProductFeatureMenus() {
        return productFeatureMenus;
    }

    public void setProductFeatureMenus(Map<String, MenuOutputConfiguration> productFeatureMenus) {
        this.productFeatureMenus = productFeatureMenus;
    }

    public Map<String, MenuOutputConfiguration> normalizedProductFeatureMenus() {
        if (productFeatureMenus == null || productFeatureMenus.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(productFeatureMenus);
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

    public LoginMenuDefinition normalizedLoginMenu() {
        return loginMenu == null ? new LoginMenuDefinition() : loginMenu;
    }
}
