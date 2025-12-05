package com.selfservice.application.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessMenuItem(
        int order,
        String label,
        String function,
        String callbackData,
        String translationKey,
        String submenuId,
        String weblink,
        String url,
        Boolean authenticated) {

    public boolean isSubMenu() {
        return submenuId != null && !submenuId.isBlank();
    }

    public boolean isWeblink() {
        return weblink != null && !weblink.isBlank();
    }

    public boolean isAction() {
        return !isSubMenu() && !isWeblink() && function != null && !function.isBlank();
    }

    public boolean isAuthenticatedLink() {
        return Boolean.TRUE.equals(authenticated);
    }
}
