package com.selfservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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

/**
 * Queries the product catalog endpoint through the common API client and maps responses into
 * simplified service summaries for messaging channels. All default query parameters are sourced
 * from configuration; this class only adds the billing account identifier required per request.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final CommonApiService commonApiService;
    private final ObjectMapper objectMapper;
    private final String serviceEndpoint;
    private final ApimanEndpointsProperties apimanEndpoints;
    private final Map<String, String> configuredQueryParams;

    public ProductService(CommonApiService commonApiService,
            ApimanEndpointsProperties apimanEndpoints,
            ObjectMapper objectMapper) {
        this.commonApiService = commonApiService;
        this.objectMapper = objectMapper;
        this.serviceEndpoint = apimanEndpoints.getProductUrl();
        this.apimanEndpoints = apimanEndpoints;
        this.configuredQueryParams = apimanEndpoints.getProductQueryParams();
        if (this.serviceEndpoint == null) {
            log.warn("APIMAN product endpoint is not configured; the service menu will be disabled.");
        }
    }

    /**
     * Fetches the main services for a billing account using the configured APIMAN product endpoint.
     *
     * @param accessToken bearer token forwarded to the downstream API
     * @param accountId   billing account identifier appended to the configured query parameters
     * @return list of simplified service summaries or an error description when the call fails
     */
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
        queryParams.put("billingAccount.id", accountId);

        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(serviceEndpoint, apimanEndpoints.getProductMethod(), accessToken,
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

        try {
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
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse service response body", e);
            return new ServiceListResult(List.of(), "Unexpected service response format.");
        }
    }

    /**
     * Pulls the "number" characteristic value from a product characteristic array when present.
     */
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

    /**
     * Safely converts a JSON node to text, returning an empty string for null nodes.
     */
    private static String safeText(JsonNode node) {
        return node == null ? "" : node.asText("");
    }
}
