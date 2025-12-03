package com.selfservice.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "service-functions")
public class ServiceFunctionProperties {

    private List<Definition> entries = new ArrayList<>();

    public List<Definition> getEntries() {
        return entries;
    }

    public void setEntries(List<Definition> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public static class Definition {
        private String name;
        private String endpoint;
        private Map<String, String> queryParams = new LinkedHashMap<>();
        private boolean accountContext;
        private boolean serviceContext;

        public String getName() {
            return StringUtils.hasText(name) ? name.trim() : null;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEndpoint() {
            return StringUtils.hasText(endpoint) ? endpoint.trim() : null;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public Map<String, String> getQueryParams() {
            return queryParams == null ? Map.of() : Map.copyOf(queryParams);
        }

        public void setQueryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(queryParams);
        }

        public boolean isAccountContext() {
            return accountContext;
        }

        public void setAccountContext(boolean accountContext) {
            this.accountContext = accountContext;
        }

        public boolean isServiceContext() {
            return serviceContext;
        }

        public void setServiceContext(boolean serviceContext) {
            this.serviceContext = serviceContext;
        }
    }
}
