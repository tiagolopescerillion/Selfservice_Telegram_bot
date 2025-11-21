package com.selfservice.whatsapp.controller;

import com.selfservice.application.auth.OAuthSessionService;
import com.selfservice.application.service.GreetingService;
import com.selfservice.application.auth.KeycloakAuthService;
import com.selfservice.whatsapp.service.WhatsappService;
import com.selfservice.whatsapp.service.WhatsappSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);

    private final WhatsappService whatsappService;
    private final GreetingService greetingService;
    private final OAuthSessionService oauthSessionService;
    private final WhatsappSessionService sessionService;
    private final String verifyToken;

    public WhatsappWebhookController(
            WhatsappService whatsappService,
            GreetingService greetingService,
            OAuthSessionService oauthSessionService,
            WhatsappSessionService sessionService,
            @Value("${whatsapp.verify-token}") String verifyToken) {
        this.whatsappService = whatsappService;
        this.greetingService = greetingService;
        this.oauthSessionService = oauthSessionService;
        this.sessionService = sessionService;
        this.verifyToken = Objects.requireNonNull(verifyToken, "whatsapp.verify-token must be set");
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        log.info("WhatsApp webhook verification request mode={} token={}", mode, token);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge == null ? "" : challenge);
        }

        return ResponseEntity.status(403).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<Void> onEvent(@RequestBody Map<String, Object> payload) {
        log.info("Incoming WhatsApp webhook: {}", payload);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
        if (entries == null) {
            return ResponseEntity.ok().build();
        }

        for (Map<String, Object> entry : entries) {
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
            if (changes == null) {
                continue;
            }

            for (Map<String, Object> change : changes) {
                Map<String, Object> value = (Map<String, Object>) change.get("value");
                if (value == null) {
                    continue;
                }

                List<Map<String, Object>> messages =
                        (List<Map<String, Object>>) value.get("messages");
                if (messages == null) {
                    continue;
                }

                for (Map<String, Object> message : messages) {
                    String from = (String) message.get("from");
                    if (from == null || from.isBlank()) {
                        continue;
                    }

                    String type = (String) message.get("type");
                    if (!"text".equals(type)) {
                        // For now we only handle text messages; everything else gets the menu.
                        whatsappService.sendHelloCerillionMenu(from);
                        continue;
                    }

                    Map<String, Object> text = (Map<String, Object>) message.get("text");
                    String body = text == null ? null : (String) text.get("body");
                    if (body == null) {
                        whatsappService.sendHelloCerillionMenu(from);
                        continue;
                    }

                    body = body.trim();

                    // Simple "menu" based on plain text input
                    if ("1".equals(body) ||
                            body.equalsIgnoreCase("hello cerillion") ||
                            body.equalsIgnoreCase("hello")) {

                        whatsappService.sendText(from, greetingService.helloCerillion());

                    } else if ("2".equals(body) || body.equalsIgnoreCase("login")) {
                        String sessionKey = "wa-" + from;
                        String loginUrl = oauthSessionService.buildAuthUrl(sessionKey);

                        StringBuilder response = new StringBuilder();
                        String existing = sessionService.getValidAccessToken(sessionKey);
                        if (existing != null) {
                            response.append("You are already logged in. Token still valid.\n\n");
                        }
                        response.append("Login via Keycloak: \n").append(loginUrl)
                                .append("\n\nAfter login you will receive a confirmation message here.");

                        whatsappService.sendText(from, response.toString());

                    } else {
                        // Default: resend the menu as plain text
                        whatsappService.sendHelloCerillionMenu(from);
                    }
                }
            }
        }

        return ResponseEntity.ok().build();
    }
}
