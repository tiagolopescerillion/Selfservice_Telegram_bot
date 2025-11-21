package com.selfservice.messenger.service;

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

import java.util.Map;

@Service
public class MessengerService {

    private static final Logger log = LoggerFactory.getLogger(MessengerService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String pageAccessToken;

    public MessengerService(@Value("${messenger.page-access-token:}") String pageAccessToken) {
        this.pageAccessToken = pageAccessToken == null ? "" : pageAccessToken.trim();

        if (!this.pageAccessToken.isBlank()) {
            log.info("Facebook Messenger page access token configured");
        }
    }

    public void sendHelloWorldMenu(String recipientId) {
        sendText(recipientId,
                "Welcome to Cerillion Bot!\n" +
                        "Please choose an option:\n" +
                        "1) Hello World\n" +
                        "(Reply with the number)");
    }

    public void sendHelloWorld(String recipientId) {
        sendText(recipientId, "Hello World \uD83D\uDC4B");
    }

    public void sendText(String recipientId, String text) {
        if (!isConfigured()) {
            log.warn("Facebook Messenger messaging is not fully configured; cannot send text");
            return;
        }

        Map<String, Object> payload = Map.of(
                "messaging_type", "RESPONSE",
                "recipient", Map.of("id", recipientId),
                "message", Map.of("text", text)
        );

        postToMessenger(payload);
    }

    private void postToMessenger(Map<String, Object> payload) {
        String url = "https://graph.facebook.com/v20.0/me/messages?access_token=" + pageAccessToken;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("Facebook Messenger API responded with status {}", response.getStatusCode());
        } catch (HttpStatusCodeException ex) {
            log.error("Facebook Messenger API error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("Failed to call Facebook Messenger API", ex);
        }
    }

    private boolean isConfigured() {
        return !pageAccessToken.isBlank();
    }
}
