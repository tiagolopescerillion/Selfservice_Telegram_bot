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
        Boolean useTranslation,
        Boolean accountContextEnabled,
        String accountContextKey,
        String accountContextLabel,
        Boolean serviceContextEnabled,
        String serviceContextKey,
        String serviceContextLabel,
        Boolean menuContextEnabled,
        String menuContextKey,
        String menuContextLabel) {

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

    @JsonIgnore
    public ContextDirectives contextDirectives() {
        return new ContextDirectives(
                Boolean.TRUE.equals(menuContextEnabled),
                sanitize(menuContextKey),
                sanitize(menuContextLabel),
                Boolean.TRUE.equals(serviceContextEnabled),
                sanitize(serviceContextKey),
                sanitize(serviceContextLabel),
                Boolean.TRUE.equals(accountContextEnabled),
                sanitize(accountContextKey),
                sanitize(accountContextLabel));
    }

    private String sanitize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ContextDirectives(
            boolean menuContextEnabled,
            String menuContextKey,
            String menuContextLabel,
            boolean serviceContextEnabled,
            String serviceContextKey,
            String serviceContextLabel,
            boolean accountContextEnabled,
            String accountContextKey,
            String accountContextLabel) {

        public String resolvedLabel() {
            if (menuContextEnabled && menuContextLabel != null && !menuContextLabel.isBlank()) {
                return menuContextLabel;
            }
            if (serviceContextEnabled && serviceContextLabel != null && !serviceContextLabel.isBlank()) {
                return serviceContextLabel;
            }
            if (accountContextEnabled && accountContextLabel != null && !accountContextLabel.isBlank()) {
                return accountContextLabel;
            }
            return null;
        }
    }
}
