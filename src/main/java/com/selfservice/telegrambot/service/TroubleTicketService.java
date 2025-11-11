package com.selfservice.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class TroubleTicketService {

    private static final Logger log = LoggerFactory.getLogger(TroubleTicketService.class);

    private final RestTemplate restTemplate;
    private final String troubleTicketEndpoint;

    public TroubleTicketService(@Qualifier("loggingRestTemplate") RestTemplate restTemplate,
            @Value("${apiman.trouble-ticket.url:${apiman.url:}}") String troubleTicketEndpoint) {
        this.restTemplate = restTemplate;
        this.troubleTicketEndpoint = (troubleTicketEndpoint == null || troubleTicketEndpoint.isBlank())
                ? null
                : troubleTicketEndpoint;
        if (this.troubleTicketEndpoint == null) {
            log.warn("APIMAN trouble-ticket endpoint is not configured; related features will be disabled.");
        }
    }

    public String callTroubleTicket(String accessToken) {
        if (troubleTicketEndpoint == null) {
            return "APIMAN[TroubleTicket] ERROR: endpoint URL is not configured.";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
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
}

