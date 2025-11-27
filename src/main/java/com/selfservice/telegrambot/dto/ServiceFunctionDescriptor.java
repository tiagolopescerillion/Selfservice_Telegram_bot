package com.selfservice.telegrambot.dto;

import java.util.List;

public record ServiceFunctionDescriptor(
        String key,
        String name,
        String url,
        boolean configured,
        List<String> services) {
}
