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
public class ExternalApiService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);
    

    private final RestTemplate restTemplate = new RestTemplate();
    private final String troubleTicketUrl;

    public ExternalApiService(@Value("${external.api.trouble-ticket.url}") String troubleTicketUrl) {
        this.troubleTicketUrl = troubleTicketUrl;
    }

    public String callTroubleTicketApi(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        headers.set("Authorization", "Bearer " + bearerToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    troubleTicketUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            log.info("External API status={} body={}", resp.getStatusCode().value(), resp.getBody());
            return "API OK (" + resp.getStatusCode().value() + "):\n" + resp.getBody();
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            log.error("API HTTP {} -> {}", ex.getStatusCode().value(), body);
            return "API ERROR: status=" + ex.getStatusCode().value() + "\n" +
                   (body == null ? "<no body>" : body);
        } catch (Exception ex) {
            log.error("API call failed", ex);
            return "API ERROR: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }
}
