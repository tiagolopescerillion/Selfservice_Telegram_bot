package com.selfservice.telegrambot.service;

import com.selfservice.telegrambot.service.dto.AccountSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
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
    public static final String BUTTON_TROUBLE_TICKET = "🎫 View trouble ticket";
    public static final String CALLBACK_HELLO_WORLD = "HELLO_WORLD";
    public static final String CALLBACK_HELLO_CERILLION = "HELLO_CERILLION";
    public static final String CALLBACK_TROUBLE_TICKET = "VIEW_TROUBLE_TICKET";
    public static final String CALLBACK_SHOW_MORE_PREFIX = "SHOW_MORE:";
    public static final String CALLBACK_ACCOUNT_PREFIX = "ACCOUNT:";


    public void sendMessage(long chatId, String text) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", text);

        post(url, body, headers);
    }

    public void sendLoginMenu(long chatId, String loginUrl) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        if (loginUrl != null && !loginUrl.isBlank()) {
            keyboard.add(List.of(Map.of(
                    "text", BUTTON_SELF_SERVICE_LOGIN,
                    "url", loginUrl)));
        } else {
            keyboard.add(List.of(Map.of(
                    "text", BUTTON_SELF_SERVICE_LOGIN,
                    "callback_data", CALLBACK_SELF_SERVICE_LOGIN)));
        }
        keyboard.add(List.of(Map.of(
                "text", BUTTON_DIRECT_LOGIN,
                "callback_data", CALLBACK_DIRECT_LOGIN)));

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

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
                                "callback_data", CALLBACK_HELLO_CERILLION)),
                        List.of(Map.of(
                                "text", BUTTON_TROUBLE_TICKET,
                                "callback_data", CALLBACK_TROUBLE_TICKET))));

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

    public void sendAccountPage(long chatId, List<AccountSummary> accounts, int startIndex) {
        Objects.requireNonNull(accounts, "accounts must not be null");

        if (accounts.isEmpty()) {
            sendMessage(chatId, "No accounts available for this user.");
            return;
        }

        int safeStart = Math.max(0, startIndex);
        if (safeStart >= accounts.size()) {
            safeStart = Math.max(0, accounts.size() - 5);
        }

        int end = Math.min(accounts.size(), safeStart + 5);

        List<List<Map<String, Object>>> rows = new ArrayList<>();
        for (int i = safeStart; i < end; i++) {
            AccountSummary summary = accounts.get(i);
            rows.add(List.of(Map.of(
                    "text", summary.displayLabel(),
                    "callback_data", CALLBACK_ACCOUNT_PREFIX + i)));
        }

        if (end < accounts.size()) {
            rows.add(List.of(Map.of(
                    "text", "Show more",
                    "callback_data", CALLBACK_SHOW_MORE_PREFIX + end)));
        }

        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", rows);
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", "Select an account:",
                "reply_markup", replyMarkup);

        post(url, body, headers);
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
