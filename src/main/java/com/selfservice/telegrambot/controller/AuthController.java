package com.selfservice.telegrambot.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @GetMapping("/auth/hello")
    public String authHello(Authentication authentication) {
        if (authentication == null) {
            return "Hello (no authentication in local profile). " +
                   "When oauth is enabled, this page will require login and show your username.";
        }
        return "Hello " + authentication.getName() + " (you are authenticated)!";
    }
}





