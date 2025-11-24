package com.selfservice.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginMenuProperties {

    private final boolean digitalLoginEnabled;
    private final boolean crmLoginEnabled;

    public LoginMenuProperties(
            @Value("${login-menu.digital-login:Y}") String digitalLoginEnabled,
            @Value("${login-menu.crm-login:Y}") String crmLoginEnabled) {
        this.digitalLoginEnabled = parseBoolean(digitalLoginEnabled, true);
        this.crmLoginEnabled = parseBoolean(crmLoginEnabled, true);
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return switch (value.trim().toUpperCase()) {
            case "Y", "YES", "TRUE", "ON", "1" -> true;
            case "N", "NO", "FALSE", "OFF", "0" -> false;
            default -> defaultValue;
        };
    }

    public boolean isDigitalLoginEnabled() {
        return digitalLoginEnabled;
    }

    public boolean isCrmLoginEnabled() {
        return crmLoginEnabled;
    }
}
