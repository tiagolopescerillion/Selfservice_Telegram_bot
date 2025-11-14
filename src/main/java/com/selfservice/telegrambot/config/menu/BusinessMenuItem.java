package com.selfservice.telegrambot.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessMenuItem(
        int order,
        String label,
        String function,
        String callbackData,
        String translationKey) {
}
