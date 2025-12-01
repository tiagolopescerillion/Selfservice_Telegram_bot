package com.selfservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfservice.application.config.ApimanEndpointsProperties;
import com.selfservice.application.dto.TroubleTicketListResult;
import com.selfservice.application.dto.TroubleTicketSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TroubleTicketService {

    private static final Logger log = LoggerFactory.getLogger(TroubleTicketService.class);

    private final CommonApiService commonApiService;
    private final String troubleTicketEndpoint;
    private final ObjectMapper objectMapper;
    private final ApimanEndpointsProperties apimanEndpoints;
    private final Map<String, String> configuredQueryParams;

    public TroubleTicketService(CommonApiService commonApiService,
            ApimanEndpointsProperties apimanEndpoints,
            ObjectMapper objectMapper) {
        this.commonApiService = commonApiService;
        this.troubleTicketEndpoint = apimanEndpoints.getTroubleTicketUrl();
        this.objectMapper = objectMapper;
        this.apimanEndpoints = apimanEndpoints;
        this.configuredQueryParams = apimanEndpoints.getTroubleTicketQueryParams();
        if (this.troubleTicketEndpoint == null) {
            log.warn("APIMAN trouble-ticket endpoint is not configured; related features will be disabled.");
        }
    }

    public String callTroubleTicket(String accessToken) {
        if (troubleTicketEndpoint == null) {
            return "APIMAN[TroubleTicket] ERROR: endpoint URL is not configured.";
        }

        Map<String, String> queryParams = new java.util.LinkedHashMap<>(configuredQueryParams);
        CommonApiService.ApiResponse resp = commonApiService.execute(
                new CommonApiService.ApiRequest(troubleTicketEndpoint, apimanEndpoints.getTroubleTicketMethod(), accessToken,
                        queryParams, null, null));

        if (!resp.success()) {
            String body = resp.body() == null ? "<no-body>" : truncate(resp.body(), 3500);
            return "APIMAN[TroubleTicket] ERROR: "
                    + (resp.statusCode() == 0 ? resp.errorMessage() : ("status=" + resp.statusCode()))
                    + "\n" + body;
        }

        int code = resp.statusCode();
        MediaType ct = resp.headers().getContentType();
        String ctype = (ct == null) ? "<none>" : ct.toString();
        int clen = (resp.body() == null) ? 0 : resp.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

        String body = (resp.body() == null) ? ""
                : resp.body();
        String preview = truncate(body, 3500);

        boolean looksHtml = preview.trim().startsWith("<!DOCTYPE") || preview.trim().startsWith("<html");
        boolean notJson = (ct == null) || !MediaType.APPLICATION_JSON.isCompatibleWith(ct);

        StringBuilder out = new StringBuilder();
        out.append("APIMAN[TroubleTicket] ").append(code).append(" (")
                .append(ctype).append(", ").append(clen).append(" bytes)\n");

        if (looksHtml || notJson) {
            out.append("⚠️ Body isn’t JSON (possible redirect, login page, or corporate splash)\n");
        }

        out.append(preview.isEmpty() ? "<empty body>" : preview);
        return out.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "\n…(truncated)" : s;
    }

    public TroubleTicketListResult getTroubleTicketsByAccountId(String accessToken, String accountId) {
        if (troubleTicketEndpoint == null) {
            return new TroubleTicketListResult(List.of(),
                    "APIMAN trouble-ticket endpoint is not configured.");
        }
        if (accessToken == null || accessToken.isBlank()) {
            return new TroubleTicketListResult(List.of(), "Missing access token.");
        }
        if (accountId == null || accountId.isBlank()) {
            return new TroubleTicketListResult(List.of(), "No billing account is selected.");
        }

        Map<String, String> queryParams = new java.util.LinkedHashMap<>(configuredQueryParams);
        queryParams.put("relatedEntity.billingAccount.id", accountId);

        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(troubleTicketEndpoint, apimanEndpoints.getTroubleTicketMethod(), accessToken,
                        queryParams, null, null));

        if (!response.success()) {
            log.error("Trouble ticket API error: {}", response.errorMessage());
            return new TroubleTicketListResult(List.of(),
                    response.statusCode() == 0
                            ? "Failed to contact trouble ticket API: " + response.errorMessage()
                            : "Trouble ticket API error " + response.statusCode());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Trouble ticket lookup returned non-success status: {}", response.statusCode());
            return new TroubleTicketListResult(List.of(),
                    "Received status " + response.statusCode() + " from trouble ticket API.");
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return new TroubleTicketListResult(List.of(), null);
        }

        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray()) {
            log.warn("Trouble ticket response was not an array: {}", body);
            return new TroubleTicketListResult(List.of(),
                    "Unexpected trouble ticket response format.");
        }

        List<TroubleTicketSummary> tickets = new ArrayList<>();
        for (JsonNode ticketNode : root) {
            String id = safeText(ticketNode.get("id"));
            if (id.isBlank()) {
                continue;
            }
            String status = safeText(ticketNode.get("status"));
            String description = firstNoteText(ticketNode);
            if (description.isBlank()) {
                description = safeText(ticketNode.get("description"));
            }
            tickets.add(new TroubleTicketSummary(id, status, description.strip()));
        }

        return new TroubleTicketListResult(tickets, null);
    }

    private static String firstNoteText(JsonNode ticketNode) {
        JsonNode notes = ticketNode.get("note");
        if (notes == null || !notes.isArray() || notes.isEmpty()) {
            return "";
        }
        JsonNode first = notes.get(0);
        if (first == null) {
            return "";
        }
        return safeText(first.get("text"));
    }

    private static String safeText(JsonNode node) {
        return node == null ? "" : node.asText("");
    }
}

