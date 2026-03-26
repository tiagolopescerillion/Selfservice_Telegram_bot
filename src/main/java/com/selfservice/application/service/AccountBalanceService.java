package com.selfservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfservice.application.config.ApimanEndpointsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Service
public class AccountBalanceService {

    private static final Logger log = LoggerFactory.getLogger(AccountBalanceService.class);

    private final CommonApiService commonApiService;
    private final ObjectMapper objectMapper;
    private final String accountEndpoint;
    private final ApimanEndpointsProperties apimanEndpoints;

    public AccountBalanceService(CommonApiService commonApiService,
            ApimanEndpointsProperties apimanEndpoints,
            ObjectMapper objectMapper) {
        this.commonApiService = commonApiService;
        this.objectMapper = objectMapper;
        this.accountEndpoint = apimanEndpoints.getAccountUrl();
        this.apimanEndpoints = apimanEndpoints;
    }

    public AccountBalanceResult lookup(String accessToken, String accountNo) {
        if (accountEndpoint == null || accountEndpoint.isBlank()) {
            return AccountBalanceResult.notDue(accountNo, false);
        }
        if (accessToken == null || accessToken.isBlank() || accountNo == null || accountNo.isBlank()) {
            return AccountBalanceResult.notDue(accountNo, false);
        }

        String targetUrl = accountEndpoint.endsWith("/")
                ? accountEndpoint + UriUtils.encodePathSegment(accountNo, StandardCharsets.UTF_8)
                : accountEndpoint + "/" + UriUtils.encodePathSegment(accountNo, StandardCharsets.UTF_8);

        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(targetUrl, apimanEndpoints.getAccountMethod(), accessToken,
                        null, null, null));

        if (!response.success() || response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null
                || response.body().isBlank()) {
            return AccountBalanceResult.notDue(accountNo, true);
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            BalanceAccumulator accumulator = new BalanceAccumulator();
            collectBalances(root, null, accumulator);
            return new AccountBalanceResult(accountNo, accumulator.current, accumulator.overdue,
                    accumulator.current.compareTo(BigDecimal.ZERO) > 0 || accumulator.overdue.compareTo(BigDecimal.ZERO) > 0,
                    true);
        } catch (Exception ex) {
            log.warn("Unable to parse account balance response for {}", accountNo, ex);
            return AccountBalanceResult.notDue(accountNo, true);
        }
    }

    private void collectBalances(JsonNode node, String parentName, BalanceAccumulator accumulator) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectBalances(child, parentName, accumulator);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (isAccountBalanceNode(parentName) || isAccountBalanceEntry(node)) {
            applyBalance(node, accumulator);
        }

        node.fields().forEachRemaining(entry -> collectBalances(entry.getValue(), entry.getKey(), accumulator));
    }

    private boolean isAccountBalanceNode(String name) {
        return normalize(name).equals("accountbalance");
    }

    private boolean isAccountBalanceEntry(JsonNode node) {
        return node.hasNonNull("type")
                && (node.has("value") || node.path("amount").has("value"))
                && !normalize(node.path("type").asText("")).isBlank();
    }

    private void applyBalance(JsonNode node, BalanceAccumulator accumulator) {
        String type = normalize(node.path("type").asText(""));
        BigDecimal value = node.has("value")
                ? decimalValue(node.path("value"))
                : decimalValue(node.path("amount").path("value"));
        if ("current".equals(type)) {
            accumulator.current = value;
        }
        if ("overdue".equals(type)) {
            accumulator.overdue = value;
        }
    }

    private BigDecimal decimalValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        try {
            return new BigDecimal(node.asText("0").trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private static final class BalanceAccumulator {
        private BigDecimal current = BigDecimal.ZERO;
        private BigDecimal overdue = BigDecimal.ZERO;
    }

    public record AccountBalanceResult(String accountNo, BigDecimal current, BigDecimal overdue, boolean hasDueBalance,
            boolean available) {
        public static AccountBalanceResult notDue(String accountNo, boolean available) {
            return new AccountBalanceResult(accountNo, BigDecimal.ZERO, BigDecimal.ZERO, false, available);
        }
    }
}

