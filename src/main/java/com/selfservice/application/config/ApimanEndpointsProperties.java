package com.selfservice.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "apiman")
public class ApimanEndpointsProperties {

    private String baseUrl;
    private Endpoint findUser = new Endpoint();
    private Endpoint accountServices = new Endpoint();
    private Endpoint troubleTicket = new Endpoint();

    public String getBaseUrl() {
        return normalize(baseUrl);
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getFindUserUrl() {
        return findUser == null ? null : findUser.getUrl();
    }

    public void setFindUser(Endpoint findUser) {
        this.findUser = findUser;
    }

    public String getAccountServicesUrl() {
        return accountServices == null ? null : accountServices.getUrl();
    }

    public void setAccountServices(Endpoint accountServices) {
        this.accountServices = accountServices;
    }

    public String getTroubleTicketUrl() {
        return troubleTicket == null ? null : troubleTicket.getUrl();
    }

    public void setTroubleTicket(Endpoint troubleTicket) {
        this.troubleTicket = troubleTicket;
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

    public static class Endpoint {
        private String url;

        public String getUrl() {
            return normalize(url);
        }

        public void setUrl(String url) {
            this.url = url;
        }

        private static String normalize(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return value.trim();
        }
    }
}
