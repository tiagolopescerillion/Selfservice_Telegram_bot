package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhook/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private final TelegramService telegramService;

    public TelegramWebhookController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @PostMapping
    public ResponseEntity<Void> onUpdate(@RequestBody Map<String, Object> update) {
        log.info("Incoming Telegram update: {}", update);

        try {
            Map<String, Object> message = (Map<String, Object>) update.get("message");
            if (message == null) return ResponseEntity.ok().build();

            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            if (chat == null || chat.get("id") == null) return ResponseEntity.ok().build();

            long chatId = ((Number) chat.get("id")).longValue();
            String text = (String) message.get("text");
            if (text == null) text = "";
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
                    // Give the user a link that will be protected once RHSSO (oauth profile) is on
                    telegramService.sendMessage(chatId,
                        "Hello Authentication 🔐\n" +
                        "Open this link to test auth:\n" + telegramService.authHelloUrl());
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
