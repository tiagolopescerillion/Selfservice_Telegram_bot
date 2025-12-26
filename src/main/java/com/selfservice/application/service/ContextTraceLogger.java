package com.selfservice.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ContextTraceLogger {
    private static final Logger log = LoggerFactory.getLogger(ContextTraceLogger.class);
    private final Environment environment;

    public ContextTraceLogger(Environment environment) {
        this.environment = environment;
    }

    public void logContext(String accountValue, String serviceValue, String objectValue) {
        if (!isFlagEnabled()) {
            return;
        }
        String account = normalize(accountValue);
        String service = normalize(serviceValue);
        String object = normalize(objectValue);
        log.info("-----------\nContext:\n- Account = {}\n- Service = {}\n- Object = {}\n-----------", account, service, object);
    }

    private boolean isFlagEnabled() {
        if (environment == null) {
            return false;
        }
        String raw = environment.getProperty("test.context", "no");
        return "yes".equalsIgnoreCase(raw.trim());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "<none>";
        }
        return value;
    }
}
