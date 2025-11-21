package com.selfservice.whatsapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class WhatsappService_backup {

    private static final Logger log = LoggerFactory.getLogger(WhatsappService_backup.class);
    public static final String HELLO_CERILLION_BUTTON_ID = "HELLO_CERILLION";

    private final RestTemplate restTemplate = new RestTemplate();
    private final String phoneNumberId;
    private final String accessToken;

    public WhatsappService_backup(
            @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.access-token:}") String accessToken) {
        this.phoneNumberId = phoneNumberId == null ? "" : phoneNumberId.trim();
        this.accessToken = accessToken == null ? "" : accessToken.trim();

        if (!this.phoneNumberId.isBlank()) {
            log.info("WhatsApp phone-number-id configured");
        }
    }

    public void sendHelloCerillionMenu(String to) {
        if (!isConfigured()) {
            log.warn("WhatsApp messaging is not fully configured; cannot send menu");
            return;
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", "Choose an option"),
                        "action", Map.of(
                                "buttons", List.of(Map.of(
                                        "type", "reply",
                                        "reply", Map.of(
                                                "id", HELLO_CERILLION_BUTTON_ID,
                                                "title", "Hello Cerillion"
                                        )
                                ))))
        );

        postToWhatsapp(payload);
    }

    public void sendText(String to, String message) {
        if (!isConfigured()) {
            log.warn("WhatsApp messaging is not fully configured; cannot send text");
            return;
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "text", Map.of("body", message)
        );

        postToWhatsapp(payload);
    }

    private void postToWhatsapp(Map<String, Object> payload) {
        String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("WhatsApp API responded with status {}", response.getStatusCode());
        } catch (HttpStatusCodeException ex) {
            log.error("WhatsApp API error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("Failed to call WhatsApp API", ex);
        }
    }

    private boolean isConfigured() {
        return !phoneNumberId.isBlank() && !accessToken.isBlank();
    }
}
