package com.selfservice.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TelegramService {
    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private final RestTemplate rest = new RestTemplate();
    private final String baseUrl;
    private final String publicBaseUrl;

    public TelegramService(
            @Value("${telegram.bot.token}") String token,
            @Value("${app.public-base-url:}") String publicBaseUrl) {

        // Enforce non-null token early (clear error if misconfigured)
        String nonNullToken = Objects.requireNonNull(
                token, "telegram.bot.token must be set in configuration");

        this.baseUrl = "https://api.telegram.org/bot" + nonNullToken;
        // Normalize possible null to empty so downstream calls (isBlank) are safe
        this.publicBaseUrl = (publicBaseUrl == null) ? "" : publicBaseUrl;

        // Mask the token when logging (don’t log secrets)
        String masked = this.baseUrl.replaceFirst("/bot[^/]+", "/bot<token>");
        log.info("Telegram baseUrl set to {}", masked);
        if (!this.publicBaseUrl.isBlank()) {
            log.info("Public base URL set to {}", this.publicBaseUrl);
        }
    }


    public static final String BUTTON_SELF_SERVICE_LOGIN = "🔑 Self-service login (APIMAN)";
    public static final String BUTTON_DIRECT_LOGIN = "⚙️ Client-credentials login (REST Server)";
    public static final String CALLBACK_SELF_SERVICE_LOGIN = "LOGIN_SELF_SERVICE";
    public static final String CALLBACK_DIRECT_LOGIN = "LOGIN_DIRECT";
    public static final String BUTTON_HELLO_WORLD = "Hello World";
    public static final String BUTTON_HELLO_CERILLION = "Hello Cerillion";
    public static final String CALLBACK_HELLO_WORLD = "HELLO_WORLD";
    public static final String CALLBACK_HELLO_CERILLION = "HELLO_CERILLION";


    public void sendMessage(long chatId, String text) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", text);

        post(url, body, headers);
    }

    public void sendLoginMenu(long chatId) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> replyMarkup = Map.of(
                "inline_keyboard", List.of(
                        List.of(Map.of(
                                "text", BUTTON_SELF_SERVICE_LOGIN,
                                "callback_data", CALLBACK_SELF_SERVICE_LOGIN)),
                        List.of(Map.of(
                                "text", BUTTON_DIRECT_LOGIN,
                                "callback_data", CALLBACK_DIRECT_LOGIN))));

        String menuText = """
                Please choose how you'd like to sign in:
                """;

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", menuText,
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendLoggedInMenu(long chatId) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> replyMarkup = Map.of(
                "inline_keyboard", List.of(
                        List.of(Map.of(
                                "text", BUTTON_HELLO_WORLD,
                                "callback_data", CALLBACK_HELLO_WORLD)),
                        List.of(Map.of(
                                "text", BUTTON_HELLO_CERILLION,
                                "callback_data", CALLBACK_HELLO_CERILLION))));

        String menuText = """
                Welcome! Choose an option:
                """;

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", menuText,
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }


    public void answerCallbackQuery(String callbackQueryId) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }

        String url = baseUrl + "/answerCallbackQuery";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("callback_query_id", callbackQueryId);

        post(url, body, headers);
    }


    public String authHelloUrl() {
        return publicBaseUrl.isBlank() ? "/auth/hello" : publicBaseUrl + "/auth/hello";
    }

    private void post(String url, Map<String, Object> body, HttpHeaders headers) {
        // Defensive null checks to satisfy static analysis
        Objects.requireNonNull(url, "url must not be null");
        if (headers == null)
            headers = new HttpHeaders();

        try {
            ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(body, headers), String.class);

            String respBody = (resp.hasBody() && resp.getBody() != null) ? resp.getBody() : "<no-body>";
            log.info("Telegram API OK status={} body={}", resp.getStatusCode().value(), respBody);

        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            if (errBody == null || errBody.isBlank())
                errBody = "<no-body>";
            log.error("Telegram API HTTP {} -> {}", ex.getStatusCode().value(), errBody, ex);

        } catch (Exception ex) {
            log.error("Telegram API call failed", ex);
        }
    }

}
