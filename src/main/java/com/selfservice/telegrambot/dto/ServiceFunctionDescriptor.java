package com.selfservice.telegrambot.dto;

import java.util.List;
import java.util.Map;

public record ServiceFunctionDescriptor(
        String key,
        String name,
        String url,
        boolean configured,
        List<String> services,
        String method,
        Map<String, String> defaultQueryParams,
        Map<String, String> configuredQueryParams,
        String queryParamKey) {
}
