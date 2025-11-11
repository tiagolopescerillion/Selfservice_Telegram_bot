package com.selfservice.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class ApimanApiService {

    private static final Logger log = LoggerFactory.getLogger(ApimanApiService.class);

    private final RestTemplate rest;
    private final String findUserEndpoint;
    private final String troubleTicketEndpoint;

    public ApimanApiService(@Qualifier("loggingRestTemplate") RestTemplate rest,
            @Value("${apiman.find-user.url:https://lonlinux13.cerillion.com:49987/apiman-gateway/CSS-MASTER-ORG/findUser/1.0?offset=0&limit=1}")
            String findUserEndpoint,
            @Value("${apiman.trouble-ticket.url:${apiman.url:}}")
            String troubleTicketEndpoint) {
        this.rest = rest;
        this.findUserEndpoint = findUserEndpoint;
        this.troubleTicketEndpoint = (troubleTicketEndpoint == null || troubleTicketEndpoint.isBlank())
                ? null
                : troubleTicketEndpoint;
        if (this.troubleTicketEndpoint == null) {
            log.warn("APIMAN trouble-ticket endpoint is not configured; related features will be disabled.");
        }
    }

    public String callFindUser(String accessToken) {
        return callEndpoint("FindUser", findUserEndpoint, accessToken);
    }

    public String callTroubleTicket(String accessToken) {
        if (troubleTicketEndpoint == null) {
            return "APIMAN ERROR: Trouble ticket endpoint is not configured.";
        }
        return callEndpoint("TroubleTicket", troubleTicketEndpoint, accessToken);
    }

    private String callEndpoint(String label, String url, String accessToken) {
        if (url == null || url.isBlank()) {
            return "APIMAN[" + label + "] ERROR: endpoint URL is not configured.";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", "SelfserviceTelegramBot/1.0"); // helps diagnose at gateway

        try {
            ResponseEntity<byte[]> resp = rest.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

            int code = resp.getStatusCode().value();
            MediaType ct = resp.getHeaders().getContentType();
            String ctype = (ct == null) ? "<none>" : ct.toString();
            int clen = (resp.getBody() == null) ? 0 : resp.getBody().length;

            String body = (resp.getBody() == null) ? ""
                    : new String(resp.getBody(), java.nio.charset.StandardCharsets.UTF_8);
            String preview = truncate(body, 3500);

            // Heuristics: If Content-Type isn’t JSON or body looks like HTML, flag it.
            boolean looksHtml = preview.trim().startsWith("<!DOCTYPE") || preview.trim().startsWith("<html");
            boolean notJson = (ct == null) || !MediaType.APPLICATION_JSON.isCompatibleWith(ct);

            StringBuilder out = new StringBuilder();
            out.append("APIMAN[").append(label).append("] ").append(code).append(" (")
                    .append(ctype).append(", ").append(clen).append(" bytes)\n");

            if (looksHtml || notJson) {
                out.append("⚠️ Body isn’t JSON (possible redirect, login page, or corporate splash)\n");
            }

            out.append(preview.isEmpty() ? "<empty body>" : preview);
            return out.toString();

        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            return "APIMAN[" + label + "] ERROR: status=" + ex.getStatusCode().value() + " ("
                    + ex.getResponseHeaders().getContentType() + ")\n"
                    + (body == null ? "<no-body>" : truncate(body, 3500));
        } catch (Exception ex) {
            return "APIMAN[" + label + "] ERROR: " + ex.getClass().getSimpleName() + ": "
                    + (ex.getMessage() == null ? "<no-message>" : ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "\n…(truncated)" : s;
    }
}
