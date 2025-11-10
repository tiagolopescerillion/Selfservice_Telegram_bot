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
            ApimanApiService apimanApiService) 
            {
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
            if (message == null)
                return ResponseEntity.ok().build();

            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            if (chat == null || chat.get("id") == null)
                return ResponseEntity.ok().build();

            long chatId = ((Number) chat.get("id")).longValue();
            String text = (String) message.get("text");
            if (text == null)
                text = "";
            text = text.trim();

            switch (text) {
                case "1":
                    telegramService.sendMessage(chatId, "Hello World 👋");
                    telegramService.sendMenu(chatId);
                    break;
                case "2":
                    telegramService.sendMessage(chatId, "Hello Cerillion 🚀");
                    telegramService.sendMenu(chatId);
                    break;

                case "3":
                    // If the user already has a valid token (from PKADMINJ_SELF), call APIMAN
                    // directly.
                    String existing = userSessionService.getValidAccessToken(chatId);
                    if (existing != null) {
                        String apiResult = apimanApiService.callWithBearer(existing);
                        telegramService.sendMessage(chatId, "Using existing login ✅\n\nAPIMAN result:\n" + apiResult);
                        telegramService.sendMenu(chatId);
                    } else {
                        String loginUrl = oauthLoginService.buildAuthUrl(chatId);
                        telegramService.sendMessage(chatId,
                                "🔐 Login required.\nTap this link to authenticate:\n" + loginUrl);
                        telegramService.sendMenu(chatId);
                    }
                    break;
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
                    telegramService.sendMenu(chatId);
                    break;
                case "/start":
                default:
                    telegramService.sendMenu(chatId);
            }
        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
        }

        return ResponseEntity.ok().build();
    }
}
