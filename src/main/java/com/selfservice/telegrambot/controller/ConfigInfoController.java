package com.selfservice.telegrambot.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
public class ConfigInfoController {

    private final Environment environment;
    private final String botToken;

    public ConfigInfoController(Environment environment,
                                @Value("${telegram.bot.token:NOT_SET}") String botToken) {
        this.environment = environment;
        this.botToken = botToken;
    }

    @GetMapping("/config-info")
    public Map<String, Object> getConfigInfo() {
        return Map.of(
                "activeProfiles", List.of(environment.getActiveProfiles()),
                "botTokenSample", botToken.isEmpty() ? "NOT SET" : "SET"
        );
    }
}
