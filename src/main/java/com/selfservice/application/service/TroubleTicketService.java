package com.selfservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfservice.application.config.ApimanEndpointsProperties;
import com.selfservice.application.dto.TroubleTicketListResult;
import com.selfservice.application.dto.TroubleTicketSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class TroubleTicketService {

    private static final Logger log = LoggerFactory.getLogger(TroubleTicketService.class);

    private final RestTemplate restTemplate;
    private final String troubleTicketEndpoint;
    private final ObjectMapper objectMapper;

    public TroubleTicketService(@Qualifier("loggingRestTemplate") RestTemplate restTemplate,
            ApimanEndpointsProperties apimanEndpoints,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.troubleTicketEndpoint = apimanEndpoints.getTroubleTicketUrl();
        this.objectMapper = objectMapper;
        if (this.troubleTicketEndpoint == null) {
            log.warn("APIMAN trouble-ticket endpoint is not configured; related features will be disabled.");
        }
    }

    public String callTroubleTicket(String accessToken) {
        if (troubleTicketEndpoint == null) {
            return "APIMAN[TroubleTicket] ERROR: endpoint URL is not configured.";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", "SelfserviceTelegramBot/1.0");

        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    troubleTicketEndpoint, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

            int code = resp.getStatusCode().value();
            MediaType ct = resp.getHeaders().getContentType();
            String ctype = (ct == null) ? "<none>" : ct.toString();
            int clen = (resp.getBody() == null) ? 0 : resp.getBody().length;

            String body = (resp.getBody() == null) ? ""
                    : new String(resp.getBody(), java.nio.charset.StandardCharsets.UTF_8);
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

        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            return "APIMAN[TroubleTicket] ERROR: status=" + ex.getStatusCode().value() + " ("
                    + ex.getResponseHeaders().getContentType() + ")\n"
                    + (body == null ? "<no-body>" : truncate(body, 3500));
        } catch (Exception ex) {
            return "APIMAN[TroubleTicket] ERROR: " + ex.getClass().getSimpleName() + ": "
                    + (ex.getMessage() == null ? "<no-message>" : ex.getMessage());
        }
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

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", "SelfserviceTelegramBot/1.0");

        final String url;
        try {
            url = UriComponentsBuilder.fromHttpUrl(troubleTicketEndpoint)
                    .queryParam("relatedEntity.billingAccount.id", accountId)
                    .build(true)
                    .toUriString();
        } catch (IllegalArgumentException ex) {
            log.error("Invalid trouble ticket endpoint configured: {}", troubleTicketEndpoint, ex);
            return new TroubleTicketListResult(List.of(), "Invalid trouble ticket endpoint URL.");
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Trouble ticket lookup returned non-success status: {}", response.getStatusCode());
                return new TroubleTicketListResult(List.of(),
                        "Received status " + response.getStatusCode().value() + " from trouble ticket API.");
            }

            String body = response.getBody();
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
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            log.error("Trouble ticket API error: status={} body={}", ex.getStatusCode().value(), body);
            return new TroubleTicketListResult(List.of(),
                    "Trouble ticket API error " + ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("Trouble ticket API call failed", ex);
            return new TroubleTicketListResult(List.of(), "Failed to contact trouble ticket API.");
        }
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

