package com.selfservice.application.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessMenuItem(
        String type,
        int order,
        String label,
        String function,
        String callbackData,
        String translationKey,
        String submenuId,
        String weblink,
        String url,
        Boolean authenticated,
        String context,
        Boolean useTranslation) {

    @JsonIgnore
    public boolean isSubMenu() {
        return submenuId != null && !submenuId.isBlank();
    }

    @JsonIgnore
    public boolean isFunctionMenu() {
        return isSubMenu() && function != null && !function.isBlank();
    }

    @JsonIgnore
    public boolean isWeblink() {
        return weblink != null && !weblink.isBlank();
    }

    @JsonIgnore
    public boolean isAction() {
        return !isSubMenu() && !isWeblink() && function != null && !function.isBlank();
    }

    @JsonIgnore
    public boolean isAuthenticatedLink() {
        return Boolean.TRUE.equals(authenticated);
    }

    @JsonIgnore
    public String linkContext() {
        if (context == null || context.isBlank()) {
            return "noContext";
        }
        return context;
    }

    @JsonIgnore
    public boolean requiresAccountContext() {
        return "account".equalsIgnoreCase(linkContext());
    }

    @JsonIgnore
    public boolean requiresServiceContext() {
        return "service".equalsIgnoreCase(linkContext());
    }
}
