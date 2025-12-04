package com.selfservice.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * UX-level toggles that influence login flows and context selection.
 */
@Component
@ConfigurationProperties(prefix = "ux")
public class UxProperties {

    /**
     * When true (default), users are guided through selecting account/service context on login.
     */
    private boolean setContext = true;

    public boolean isSetContext() {
        return setContext;
    }

    public void setSetContext(boolean setContext) {
        this.setContext = setContext;
    }
}
