package com.selfservice.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "apiman")
public class ApimanEndpointsProperties {

    private String baseUrl;
    private String findUserUrl;
    private String accountServicesUrl;
    private String troubleTicketUrl;

    public String getBaseUrl() {
        return normalize(baseUrl);
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getFindUserUrl() {
        return normalize(findUserUrl);
    }

    public void setFindUserUrl(String findUserUrl) {
        this.findUserUrl = findUserUrl;
    }

    public String getAccountServicesUrl() {
        return normalize(accountServicesUrl);
    }

    public void setAccountServicesUrl(String accountServicesUrl) {
        this.accountServicesUrl = accountServicesUrl;
    }

    public String getTroubleTicketUrl() {
        return normalize(troubleTicketUrl);
    }

    public void setTroubleTicketUrl(String troubleTicketUrl) {
        this.troubleTicketUrl = troubleTicketUrl;
    }

    public boolean hasFindUser() {
        return getFindUserUrl() != null;
    }

    public boolean hasAccountServices() {
        return getAccountServicesUrl() != null;
    }

    public boolean hasTroubleTicket() {
        return getTroubleTicketUrl() != null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
