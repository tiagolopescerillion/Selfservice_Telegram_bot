package com.selfservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfservice.application.dto.ServiceListResult;
import com.selfservice.application.dto.ServiceSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String serviceEndpoint;

    public MainServiceCatalogService(@Qualifier("loggingRestTemplate") RestTemplate restTemplate,
            @Value("${apiman.account-services.url:${apiman.url:}}") String serviceEndpoint,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.serviceEndpoint = (serviceEndpoint == null || serviceEndpoint.isBlank()) ? null : serviceEndpoint;
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

        final String url;
        try {
            url = UriComponentsBuilder.fromHttpUrl(serviceEndpoint)
                    .queryParam("offset", DEFAULT_OFFSET)
                    .queryParam("limit", DEFAULT_LIMIT)
                    .queryParam("isMainService", DEFAULT_IS_MAIN_SERVICE)
                    .queryParam("isVisible", DEFAULT_IS_VISIBLE)
                    .queryParam("subStatus", DEFAULT_SUB_STATUS)
                    .queryParam("completePackages", DEFAULT_COMPLETE_PACKAGES)
                    .queryParam("fields", DEFAULT_FIELDS)
                    .queryParam("billingAccount.id", accountId)
                    .build(true)
                    .toUriString();
        } catch (IllegalArgumentException ex) {
            log.error("Invalid service endpoint configured: {}", serviceEndpoint, ex);
            return new ServiceListResult(List.of(), "Invalid service endpoint URL.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", "SelfserviceTelegramBot/1.0");

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Service lookup returned non-success status: {}", response.getStatusCode());
                return new ServiceListResult(List.of(),
                        "Received status " + response.getStatusCode().value() + " from service API.");
            }

            String body = response.getBody();
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
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            log.error("Service API error: status={} body={}", ex.getStatusCode().value(), body);
            return new ServiceListResult(List.of(), "Service API error " + ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("Service API call failed", ex);
            return new ServiceListResult(List.of(), "Failed to contact service API.");
        }
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
