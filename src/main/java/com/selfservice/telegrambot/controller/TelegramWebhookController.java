package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.UserSessionService;
import com.selfservice.telegrambot.service.ApimanApiService;
import com.selfservice.telegrambot.service.OAuthLoginService;

import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.telegrambot.service.KeycloakAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.selfservice.telegrambot.service.ExternalApiService;

import java.util.Map;

@RestController
@RequestMapping("/webhook/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private final TelegramService telegramService;
    private final KeycloakAuthService keycloakAuthService;
    private final ExternalApiService externalApiService;
    private final OAuthLoginService oauthLoginService;
    private final UserSessionService userSessionService;
    private final ApimanApiService apimanApiService;

    public TelegramWebhookController(TelegramService telegramService,
            KeycloakAuthService keycloakAuthService,
            ExternalApiService externalApiService,
            OAuthLoginService oauthLoginService,
            UserSessionService userSessionService,
            ApimanApiService apimanApiService) {
        this.telegramService = telegramService;
        this.keycloakAuthService = keycloakAuthService;
        this.externalApiService = externalApiService;
        this.oauthLoginService = oauthLoginService;

        this.userSessionService = userSessionService;
        this.apimanApiService = apimanApiService;

    }

    @PostMapping
    public ResponseEntity<Void> onUpdate(@RequestBody Map<String, Object> update) {
        log.info("Incoming Telegram update: {}", update);

        try {

            Map<String, Object> message = (Map<String, Object>) update.get("message");
            Map<String, Object> callbackQuery = (Map<String, Object>) update.get("callback_query");
            Map<String, Object> chat;
            String text;
            long chatId;

            if (message != null) {
                chat = (Map<String, Object>) message.get("chat");
                if (chat == null || chat.get("id") == null)
                    return ResponseEntity.ok().build();

                chatId = ((Number) chat.get("id")).longValue();
                text = (String) message.get("text");
                if (text == null)
                    text = "";
                text = text.trim();
            } else if (callbackQuery != null) {
                Map<String, Object> callbackMessage = (Map<String, Object>) callbackQuery.get("message");
                if (callbackMessage == null)
                    return ResponseEntity.ok().build();

                chat = (Map<String, Object>) callbackMessage.get("chat");
                if (chat == null || chat.get("id") == null)
                    return ResponseEntity.ok().build();

                chatId = ((Number) chat.get("id")).longValue();
                text = (String) callbackQuery.get("data");
                if (text == null)
                    text = "";
                text = text.trim();

                telegramService.answerCallbackQuery((String) callbackQuery.get("id"));
            } else {
            

                return ResponseEntity.ok().build();

            }

            String existingToken = userSessionService.getValidAccessToken(chatId);
            boolean hasValidToken = existingToken != null;
            String loginReminder = "Please login using the \"" + TelegramService.BUTTON_SELF_SERVICE_LOGIN
                    + "\" button to access this feature.";

            switch (text) {
                case TelegramService.CALLBACK_HELLO_WORLD:
                case TelegramService.BUTTON_HELLO_WORLD:
                case "1":
                    if (hasValidToken) {
                        telegramService.sendMessage(chatId, "Hello World 👋");
                        telegramService.sendLoggedInMenu(chatId);
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId);
                    }
                    break;
                case TelegramService.CALLBACK_HELLO_CERILLION:
                case TelegramService.BUTTON_HELLO_CERILLION:
                case "2":
                    if (hasValidToken) {
                        telegramService.sendMessage(chatId, "Hello Cerillion 🚀");
                        telegramService.sendLoggedInMenu(chatId);
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId);
                    }
                    break;
                case TelegramService.CALLBACK_SELF_SERVICE_LOGIN:
                case TelegramService.BUTTON_SELF_SERVICE_LOGIN:
                case "3":
                    if (hasValidToken) {
                        String apiResult = apimanApiService.callWithBearer(existingToken);
                        telegramService.sendMessage(chatId, "Using existing login ✅\n\nAPIMAN result:\n" + apiResult);
                        telegramService.sendLoggedInMenu(chatId);
                    } else {
                        String loginUrl = oauthLoginService.buildAuthUrl(chatId);
                        telegramService.sendMessage(chatId,
                                "🔐 Login required.\nTap this link to authenticate:\n" + loginUrl);
                        telegramService.sendLoginMenu(chatId);
                    }
                    break;
                case TelegramService.CALLBACK_DIRECT_LOGIN:
                case TelegramService.BUTTON_DIRECT_LOGIN:
                case "4":
                    // Step 1: Authenticate and get token
                    String token = null;
                    String authMessage;
                    try {
                        token = keycloakAuthService.getAccessToken();
                        authMessage = "Auth OK ✅\nToken retrieved successfully.";
                    } catch (Exception e) {
                        authMessage = "Auth ERROR ❌: " + e.getMessage();
                    }

                    // Step 2: Call the external API using the token
                    String apiResponse = "No API response.";
                    if (token != null) {
                        apiResponse = externalApiService.callTroubleTicketApi(token);
                    }

                    // Step 3: Combine and send to Telegram
                    telegramService.sendMessage(chatId,
                            authMessage + "\n\n" +
                                    "External API result:\n" + apiResponse);
                    telegramService.sendLoginMenu(chatId);
                    break;
                case "/start":
                default:
                    if (hasValidToken) {
                        telegramService.sendLoggedInMenu(chatId);
                    } else {
                        telegramService.sendLoginMenu(chatId);
                    }
            }
        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
        }

        return ResponseEntity.ok().build();
    }
}
