package com.selfservice.application.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Comparator;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessMenuDefinition {
    public static final String ROOT_MENU_ID = "home";

    private String id;
    private String name;
    private List<BusinessMenuItem> items;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<BusinessMenuItem> getItems() {
        return items;
    }

    public void setItems(List<BusinessMenuItem> items) {
        this.items = items;
    }

    public List<BusinessMenuItem> sortedItems() {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .sorted(Comparator.comparingInt(BusinessMenuItem::order))
                .toList();
    }
}
