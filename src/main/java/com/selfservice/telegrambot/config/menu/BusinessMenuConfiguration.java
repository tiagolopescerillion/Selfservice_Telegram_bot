package com.selfservice.telegrambot.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Comparator;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessMenuConfiguration {
    private int version;
    private String generatedAt;
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

    public List<BusinessMenuItem> getMenu() {
        return menu;
    }

    public void setMenu(List<BusinessMenuItem> menu) {
        this.menu = menu;
    }

    public List<BusinessMenuItem> sortedMenuItems() {
        if (menu == null) {
            return List.of();
        }
        return menu.stream()
                .sorted(Comparator.comparingInt(BusinessMenuItem::order))
                .toList();
    }
}
