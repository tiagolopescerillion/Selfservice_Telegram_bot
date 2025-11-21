package com.selfservice.whatsapp.controller;

import com.selfservice.application.service.GreetingService;
import com.selfservice.whatsapp.service.WhatsappService_backup;
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
public class WhatsappWebhookController_backup {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController_backup.class);

    private final WhatsappService_backup whatsappService;
    private final GreetingService greetingService;
    private final String verifyToken;

    public WhatsappWebhookController_backup(
            WhatsappService_backup whatsappService,
            GreetingService greetingService,
            @Value("${whatsapp.verify-token}") String verifyToken) {
        this.whatsappService = whatsappService;
        this.greetingService = greetingService;
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
                List<Map<String, Object>> messages = (List<Map<String, Object>>) value.get("messages");
                if (messages == null) {
                    continue;
                }
                for (Map<String, Object> message : messages) {
                    String from = (String) message.get("from");
                    if (from == null || from.isBlank()) {
                        continue;
                    }

                    Map<String, Object> interactive = (Map<String, Object>) message.get("interactive");
                    if (interactive != null) {
                        Map<String, Object> buttonReply = (Map<String, Object>) interactive.get("button_reply");
                        if (buttonReply != null) {
                            String buttonId = (String) buttonReply.get("id");
                            if (WhatsappService_backup.HELLO_CERILLION_BUTTON_ID.equals(buttonId)) {
                                whatsappService.sendText(from, greetingService.helloCerillion());
                                continue;
                            }
                        }
                    }

                    // Default: send a minimal menu with a single Hello Cerillion option
                    whatsappService.sendHelloCerillionMenu(from);
                }
            }
        }

        return ResponseEntity.ok().build();
    }
}
