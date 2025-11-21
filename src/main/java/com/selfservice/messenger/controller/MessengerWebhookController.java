package com.selfservice.messenger.controller;

import com.selfservice.messenger.service.MessengerService;
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
@RequestMapping("/messenger/webhook")
public class MessengerWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MessengerWebhookController.class);

    private final MessengerService messengerService;
    private final String verifyToken;

    public MessengerWebhookController(
            MessengerService messengerService,
            @Value("${messenger.verify-token}") String verifyToken) {
        this.messengerService = messengerService;
        this.verifyToken = Objects.requireNonNull(verifyToken, "messenger.verify-token must be set");
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        log.info("Messenger webhook verification request mode={} token={}", mode, token);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge == null ? "" : challenge);
        }

        return ResponseEntity.status(403).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<Void> onEvent(@RequestBody Map<String, Object> payload) {
        log.info("Incoming Messenger webhook: {}", payload);

        if (!"page".equals(payload.get("object"))) {
            return ResponseEntity.ok().build();
        }

        List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
        if (entries == null) {
            return ResponseEntity.ok().build();
        }

        for (Map<String, Object> entry : entries) {
            List<Map<String, Object>> messagingList = (List<Map<String, Object>>) entry.get("messaging");
            if (messagingList == null) {
                continue;
            }

            for (Map<String, Object> messagingEvent : messagingList) {
                Map<String, Object> sender = (Map<String, Object>) messagingEvent.get("sender");
                String senderId = sender == null ? null : (String) sender.get("id");
                if (senderId == null || senderId.isBlank()) {
                    continue;
                }

                Map<String, Object> message = (Map<String, Object>) messagingEvent.get("message");
                if (message == null) {
                    messengerService.sendHelloWorldMenu(senderId);
                    continue;
                }

                Object textObj = message.get("text");
                String text = textObj instanceof String ? ((String) textObj).trim() : "";

                if (text.isEmpty()) {
                    messengerService.sendHelloWorldMenu(senderId);
                    continue;
                }

                if ("1".equals(text) || text.equalsIgnoreCase("hello world") || text.equalsIgnoreCase("hello")) {
                    messengerService.sendHelloWorld(senderId);
                } else {
                    messengerService.sendHelloWorldMenu(senderId);
                }
            }
        }

        return ResponseEntity.ok().build();
    }
}
