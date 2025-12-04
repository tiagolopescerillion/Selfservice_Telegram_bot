package com.selfservice.application.controller;

import com.selfservice.application.config.ConnectorsProperties;
import com.selfservice.application.service.OperationsMonitoringService;
import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.telegrambot.service.UserSessionService;
import com.selfservice.whatsapp.service.WhatsappService;
import com.selfservice.whatsapp.service.WhatsappSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@CrossOrigin
public class NotificationController {

    private final TelegramService telegramService;
    private final WhatsappService whatsappService;
    private final UserSessionService userSessionService;
    private final WhatsappSessionService whatsappSessionService;
    private final OperationsMonitoringService monitoringService;
    private final ConnectorsProperties connectorsProperties;

    public NotificationController(TelegramService telegramService,
                                  WhatsappService whatsappService,
                                  UserSessionService userSessionService,
                                  WhatsappSessionService whatsappSessionService,
                                  OperationsMonitoringService monitoringService,
                                  ConnectorsProperties connectorsProperties) {
        this.telegramService = telegramService;
        this.whatsappService = whatsappService;
        this.userSessionService = userSessionService;
        this.whatsappSessionService = whatsappSessionService;
        this.monitoringService = monitoringService;
        this.connectorsProperties = connectorsProperties;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> sendNotification(@RequestBody NotificationRequest request) {
        if (request == null || request.message == null || request.message.isBlank()
                || request.channel == null || request.channel.isBlank()
                || request.chatId == null || request.chatId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "rejected",
                    "reason", "Channel, chat ID, and message are required"
            ));
        }

        String normalizedChannel = request.channel.trim().toLowerCase();
        String message = request.message.trim();
        switch (normalizedChannel) {
            case "telegram" -> {
                if (!connectorsProperties.isTelegramEnabled()) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                            "status", "rejected",
                            "reason", "Telegram connector is disabled"
                    ));
                }
                long chatId;
                try {
                    chatId = Long.parseLong(request.chatId.trim());
                } catch (NumberFormatException e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                            "status", "rejected",
                            "reason", "Chat ID must be a number for Telegram"
                    ));
                }
                if (!userSessionService.isOptedIn(chatId)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                            "status", "rejected",
                            "reason", "User has not opt-in for receiving messages"
                    ));
                }
                telegramService.sendMessage(chatId, message);
                monitoringService.recordActivity("Telegram", Long.toString(chatId), null,
                        userSessionService.getValidAccessToken(chatId) != null,
                        monitoringService.toTokenDetails(userSessionService.getTokenSnapshot(chatId)), true);
                return ResponseEntity.ok(Map.of(
                        "status", "sent",
                        "channel", "Telegram",
                        "chatId", Long.toString(chatId)
                ));
            }
            case "whatsapp", "wa" -> {
                if (!connectorsProperties.isWhatsappEnabled()) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                            "status", "rejected",
                            "reason", "WhatsApp connector is disabled"
                    ));
                }
                String chatId = request.chatId.trim();
                if (!whatsappSessionService.isOptedIn(chatId)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                            "status", "rejected",
                            "reason", "User has not opt-in for receiving messages"
                    ));
                }
                whatsappService.sendText(chatId, message);
                monitoringService.recordActivity("WhatsApp", chatId, null,
                        whatsappSessionService.getValidAccessToken(chatId) != null,
                        monitoringService.toTokenDetails(whatsappSessionService.getTokenSnapshot(chatId)), true);
                return ResponseEntity.ok(Map.of(
                        "status", "sent",
                        "channel", "WhatsApp",
                        "chatId", chatId
                ));
            }
            default -> {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "status", "rejected",
                        "reason", "Unsupported channel"
                ));
            }
        }
    }

    public static final class NotificationRequest {
        public String channel;
        public String chatId;
        public String message;
    }
}
