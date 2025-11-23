package com.selfservice.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginMenuProperties {

    private final boolean digitalLoginEnabled;
    private final boolean crmLoginEnabled;

    public LoginMenuProperties(
            @Value("${login-menu.digital-login:true}") boolean digitalLoginEnabled,
            @Value("${login-menu.crm-login:true}") boolean crmLoginEnabled) {
        this.digitalLoginEnabled = digitalLoginEnabled;
        this.crmLoginEnabled = crmLoginEnabled;
    }

    public boolean isDigitalLoginEnabled() {
        return digitalLoginEnabled;
    }

    public boolean isCrmLoginEnabled() {
        return crmLoginEnabled;
    }
}
