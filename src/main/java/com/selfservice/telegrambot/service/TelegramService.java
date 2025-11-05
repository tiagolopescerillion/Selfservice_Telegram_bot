package com.selfservice.telegrambot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class TelegramService {

    private final WebClient webClient;

    public TelegramService(@Value("${telegram.bot.token}") String token) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.telegram.org/bot" + token)
                .build();
    }

    public void sendMessage(Long chatId, String text) {
        webClient.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .bodyToMono(String.class)
                .subscribe();
    }
}
