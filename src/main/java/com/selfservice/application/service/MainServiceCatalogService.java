package com.selfservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfservice.application.config.ApimanEndpointsProperties;
import com.selfservice.application.dto.ServiceListResult;
import com.selfservice.application.dto.ServiceSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MainServiceCatalogService {

    private static final Logger log = LoggerFactory.getLogger(MainServiceCatalogService.class);

    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_LIMIT = 50;
    private static final boolean DEFAULT_IS_MAIN_SERVICE = true;
    private static final boolean DEFAULT_IS_VISIBLE = true;
    private static final String DEFAULT_SUB_STATUS = "CU,FA,TA,RP,TP";
    private static final boolean DEFAULT_COMPLETE_PACKAGES = true;
    private static final String DEFAULT_FIELDS = "id,isBundle,description,subStatus,isMainService,serviceType,productRelationship,productCharacteristic,billingAccount";

    private final CommonApiService commonApiService;
    private final ObjectMapper objectMapper;
    private final String serviceEndpoint;
    private final ApimanEndpointsProperties apimanEndpoints;
    private final Map<String, String> configuredQueryParams;

    public MainServiceCatalogService(CommonApiService commonApiService,
            ApimanEndpointsProperties apimanEndpoints,
            ObjectMapper objectMapper) {
        this.commonApiService = commonApiService;
        this.objectMapper = objectMapper;
        this.serviceEndpoint = apimanEndpoints.getAccountServicesUrl();
        this.apimanEndpoints = apimanEndpoints;
        this.configuredQueryParams = apimanEndpoints.getAccountServicesQueryParams();
        if (this.serviceEndpoint == null) {
            log.warn("APIMAN account-services endpoint is not configured; the service menu will be disabled.");
        }
    }

    public ServiceListResult getMainServices(String accessToken, String accountId) {
        if (serviceEndpoint == null) {
            return new ServiceListResult(List.of(), "APIMAN service endpoint is not configured.");
        }
        if (accessToken == null || accessToken.isBlank()) {
            return new ServiceListResult(List.of(), "Missing access token.");
        }
        if (accountId == null || accountId.isBlank()) {
            return new ServiceListResult(List.of(), "No billing account is selected.");
        }

        Map<String, String> queryParams = new java.util.LinkedHashMap<>(configuredQueryParams);
        queryParams.putIfAbsent("offset", String.valueOf(DEFAULT_OFFSET));
        queryParams.putIfAbsent("limit", String.valueOf(DEFAULT_LIMIT));
        queryParams.putIfAbsent("isMainService", String.valueOf(DEFAULT_IS_MAIN_SERVICE));
        queryParams.putIfAbsent("isVisible", String.valueOf(DEFAULT_IS_VISIBLE));
        queryParams.putIfAbsent("subStatus", DEFAULT_SUB_STATUS);
        queryParams.putIfAbsent("completePackages", String.valueOf(DEFAULT_COMPLETE_PACKAGES));
        queryParams.putIfAbsent("fields", DEFAULT_FIELDS);
        queryParams.put("billingAccount.id", accountId);

        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(serviceEndpoint, apimanEndpoints.getAccountServicesMethod(), accessToken,
                        queryParams, null, null));

        if (!response.success()) {
            log.warn("Service lookup returned error status: {}", response.errorMessage());
            return new ServiceListResult(List.of(),
                    response.statusCode() == 0
                            ? "Failed to contact service API: " + response.errorMessage()
                            : "Service API error " + response.statusCode());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Service lookup returned non-success status: {}", response.statusCode());
            return new ServiceListResult(List.of(),
                    "Received status " + response.statusCode() + " from service API.");
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return new ServiceListResult(List.of(), null);
        }

        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray()) {
            log.warn("Service response was not an array: {}", body);
            return new ServiceListResult(List.of(), "Unexpected service response format.");
        }

        List<ServiceSummary> services = new ArrayList<>();
        for (JsonNode node : root) {
            String id = safeText(node.get("id"));
            if (id.isBlank()) {
                continue;
            }
            String description = safeText(node.get("description"));
            String accessNumber = extractAccessNumber(node.get("productCharacteristic"));
            services.add(new ServiceSummary(id, description.strip(), accessNumber.strip()));
        }

        return new ServiceListResult(services, null);
    }

    private static String extractAccessNumber(JsonNode characteristicsNode) {
        if (characteristicsNode == null || !characteristicsNode.isArray()) {
            return "";
        }
        for (JsonNode characteristic : characteristicsNode) {
            String name = safeText(characteristic.get("name"));
            if (name.equalsIgnoreCase("number")) {
                return safeText(characteristic.get("value"));
            }
        }
        return "";
    }

    private static String safeText(JsonNode node) {
        return node == null ? "" : node.asText("");
    }
}
