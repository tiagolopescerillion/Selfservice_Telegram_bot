package com.selfservice.application.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessMenuItem(
        int order,
        String label,
        String function,
        String callbackData,
        String translationKey,
        String submenuId) {

    public boolean isSubMenu() {
        return submenuId != null && !submenuId.isBlank();
    }

    public boolean isAction() {
        return !isSubMenu() && function != null && !function.isBlank();
    }
}
