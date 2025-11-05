package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.TelegramService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            // Extract message info
            Map<String, Object> message = (Map<String, Object>) update.get("message");
            if (message != null) {
                Long chatId = ((Number) ((Map<String, Object>) message.get("chat")).get("id")).longValue();
                String text = (String) message.get("text");

                // Simple menu
                switch (text.trim()) {
                    case "1":
                        telegramService.sendMessage(chatId, "Hello World 👋");
                        break;
                    case "2":
                        telegramService.sendMessage(chatId, "Hello Cerillion 🚀");
                        break;
                    default:
                        telegramService.sendMessage(chatId,
                                "Please choose an option:\n1 - Hello World\n2 - Hello Cerillion");
                }
            }
        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
        }

        return ResponseEntity.ok().build();
    }
}
