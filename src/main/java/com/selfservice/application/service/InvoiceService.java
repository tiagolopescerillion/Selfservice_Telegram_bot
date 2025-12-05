package com.selfservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfservice.application.config.ApimanEndpointsProperties;
import com.selfservice.application.dto.InvoiceListResult;
import com.selfservice.application.dto.InvoiceSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Retrieves invoice history for a billing account using the shared API client. */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final CommonApiService commonApiService;
    private final ObjectMapper objectMapper;
    private final String billEndpoint;
    private final ApimanEndpointsProperties apimanEndpoints;
    private final Map<String, String> configuredQueryParams;

    public InvoiceService(CommonApiService commonApiService,
            ApimanEndpointsProperties apimanEndpoints,
            ObjectMapper objectMapper) {
        this.commonApiService = commonApiService;
        this.objectMapper = objectMapper;
        this.billEndpoint = apimanEndpoints.getBillUrl();
        this.apimanEndpoints = apimanEndpoints;
        this.configuredQueryParams = apimanEndpoints.getBillQueryParams();
        if (this.billEndpoint == null) {
            log.warn("APIMAN bill endpoint is not configured; invoice history will be disabled.");
        }
    }

    public InvoiceListResult getInvoices(String accessToken, String accountId) {
        if (billEndpoint == null) {
            return new InvoiceListResult(List.of(), "APIMAN bill endpoint is not configured.");
        }
        if (accessToken == null || accessToken.isBlank()) {
            return new InvoiceListResult(List.of(), "Missing access token.");
        }
        if (accountId == null || accountId.isBlank()) {
            return new InvoiceListResult(List.of(), "No billing account is selected.");
        }

        Map<String, String> queryParams = new java.util.LinkedHashMap<>(configuredQueryParams);
        queryParams.put("billingAccount.id", accountId);

        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(billEndpoint, apimanEndpoints.getBillMethod(), accessToken, queryParams, null,
                        null));

        if (!response.success()) {
            log.warn("Invoice lookup returned error status: {}", response.errorMessage());
            return new InvoiceListResult(List.of(),
                    response.statusCode() == 0
                            ? "Failed to contact invoice API: " + response.errorMessage()
                            : "Invoice API error " + response.statusCode());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Invoice lookup returned non-success status: {}", response.statusCode());
            return new InvoiceListResult(List.of(),
                    "Received status " + response.statusCode() + " from invoice API.");
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return new InvoiceListResult(List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                log.warn("Invoice response was not an array: {}", body);
                return new InvoiceListResult(List.of(), "Unexpected invoice response format.");
            }

            List<InvoiceSummary> invoices = new ArrayList<>();
            for (JsonNode node : root) {
                String id = safeText(node.get("id"));
                if (id.isBlank()) {
                    continue;
                }
                String billDate = safeText(node.get("billDate"));
                String total = formatAmount(node.get("amountDue"));
                String unpaid = formatAmount(node.get("remainingAmount"));
                invoices.add(new InvoiceSummary(id.strip(), billDate.strip(), total.strip(), unpaid.strip()));
            }

            return new InvoiceListResult(invoices, null);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse invoice response body", e);
            return new InvoiceListResult(List.of(), "Unexpected invoice response format.");
        }
    }

    private static String formatAmount(JsonNode amountNode) {
        if (amountNode == null || amountNode.isNull()) {
            return "";
        }
        String unit = safeText(amountNode.get("unit"));
        String value = safeText(amountNode.get("value"));
        String combined = (unit + " " + value).trim();
        return combined.isEmpty() ? value.strip() : combined;
    }

    private static String safeText(JsonNode node) {
        return node == null ? "" : node.asText("");
    }
}

