package com.selfservice.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "apiman")
public class ApimanEndpointsProperties {

    private String baseUrl;
    private Endpoint findUser = new Endpoint();
    private Endpoint product = new Endpoint();
    private Endpoint troubleTicket = new Endpoint();
    private Endpoint impersonationInitiate = new Endpoint();
    private Endpoint bill = new Endpoint();

    private static final Map<String, String> DEFAULT_FIND_USER_QUERY_PARAMS = Map.of(
            "offset", "0",
            "limit", "1"
    );

    private static final Map<String, String> DEFAULT_PRODUCT_QUERY_PARAMS = Map.of(
            "offset", "0",
            "limit", "50",
            "isMainService", "true",
            "isVisible", "true",
            "subStatus", "CU,FA,TA,RP,TP",
            "completePackages", "true",
            "fields", "id,isBundle,description,subStatus,isMainService,serviceType,productRelationship,productCharacteristic,billingAccount"
    );

    private static final Map<String, String> DEFAULT_TROUBLE_TICKET_QUERY_PARAMS = Map.of(
            "relatedEntity.billingAccount.id", ""
    );

    private static final Map<String, String> DEFAULT_BILL_QUERY_PARAMS = Map.of(
            "offset", "0",
            "limit", "6",
            "billingAccount.id", "",
            "sort", "-billDate"
    );

    public String getBaseUrl() {
        return normalize(baseUrl);
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getFindUserUrl() {
        return findUser == null ? null : findUser.getUrl();
    }

    public HttpMethod getFindUserMethod() {
        return resolveMethod(findUser);
    }

    public Map<String, String> getFindUserQueryParams() {
        return resolveQueryParams(findUser, DEFAULT_FIND_USER_QUERY_PARAMS);
    }

    public Map<String, String> getDefaultFindUserQueryParams() {
        return DEFAULT_FIND_USER_QUERY_PARAMS;
    }

    public void setFindUser(Endpoint findUser) {
        this.findUser = findUser;
    }

    public String getProductUrl() {
        return product == null ? null : product.getUrl();
    }

    public HttpMethod getProductMethod() {
        return resolveMethod(product);
    }

    public Map<String, String> getProductQueryParams() {
        return resolveQueryParams(product, DEFAULT_PRODUCT_QUERY_PARAMS);
    }

    public Map<String, String> getDefaultProductQueryParams() {
        return DEFAULT_PRODUCT_QUERY_PARAMS;
    }

    public void setProduct(Endpoint product) {
        this.product = product;
    }

    /**
     * Temporary backward-compatibility shim to support legacy configuration keys
     * using "account-services". New deployments should migrate to "product".
     */
    @Deprecated(forRemoval = true)
    public void setAccountServices(Endpoint accountServices) {
        this.product = accountServices;
    }

    public String getTroubleTicketUrl() {
        return troubleTicket == null ? null : troubleTicket.getUrl();
    }

    public HttpMethod getTroubleTicketMethod() {
        return resolveMethod(troubleTicket);
    }

    public Map<String, String> getTroubleTicketQueryParams() {
        return resolveQueryParams(troubleTicket, DEFAULT_TROUBLE_TICKET_QUERY_PARAMS);
    }

    public Map<String, String> getDefaultTroubleTicketQueryParams() {
        return DEFAULT_TROUBLE_TICKET_QUERY_PARAMS;
    }

    public void setTroubleTicket(Endpoint troubleTicket) {
        this.troubleTicket = troubleTicket;
    }

    public String getBillUrl() {
        return bill == null ? null : bill.getUrl();
    }

    public HttpMethod getBillMethod() {
        return resolveMethod(bill);
    }

    public Map<String, String> getBillQueryParams() {
        return resolveQueryParams(bill, DEFAULT_BILL_QUERY_PARAMS);
    }

    public Map<String, String> getDefaultBillQueryParams() {
        return DEFAULT_BILL_QUERY_PARAMS;
    }

    public void setBill(Endpoint bill) {
        this.bill = bill;
    }

    public String getImpersonationInitiateUrl() {
        if (impersonationInitiate != null && impersonationInitiate.getUrl() != null) {
            return impersonationInitiate.getUrl();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return baseUrl + "/impersonationInitiate/1.0";
    }

    public HttpMethod getImpersonationInitiateMethod() {
        return resolveMethod(impersonationInitiate);
    }

    public void setImpersonationInitiate(Endpoint impersonationInitiate) {
        this.impersonationInitiate = impersonationInitiate;
    }

    public boolean hasFindUser() {
        return getFindUserUrl() != null;
    }

    public boolean hasProduct() {
        return getProductUrl() != null;
    }

    public boolean hasTroubleTicket() {
        return getTroubleTicketUrl() != null;
    }

    private HttpMethod resolveMethod(Endpoint endpoint) {
        if (endpoint == null || endpoint.getMethod() == null) {
            return HttpMethod.GET;
        }
        return endpoint.getMethod();
    }

    private Map<String, String> resolveQueryParams(Endpoint endpoint, Map<String, String> defaults) {
        Map<String, String> resolved = new LinkedHashMap<>(defaults);
        if (endpoint != null && !endpoint.getQueryParams().isEmpty()) {
            resolved.putAll(endpoint.getQueryParams());
        }
        return Map.copyOf(resolved);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public static class Endpoint {
        private String url;
        private HttpMethod method = HttpMethod.GET;
        private Map<String, String> queryParams = new LinkedHashMap<>();

        public String getUrl() {
            return normalize(url);
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public HttpMethod getMethod() {
            return method == null ? HttpMethod.GET : method;
        }

        public void setMethod(HttpMethod method) {
            this.method = method;
        }

        public Map<String, String> getQueryParams() {
            return queryParams == null ? Map.of() : Map.copyOf(queryParams);
        }

        public void setQueryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(queryParams);
        }

        private static String normalize(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return value.trim();
        }
    }
}
