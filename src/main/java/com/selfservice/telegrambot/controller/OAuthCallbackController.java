package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.OAuthLoginService;
import com.selfservice.telegrambot.service.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@RestController
public class OAuthCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackController.class);

    private final OAuthLoginService oauth;
    private final TelegramService telegram;

    public OAuthCallbackController(OAuthLoginService oauth, TelegramService telegram) {
        this.oauth = oauth;
        this.telegram = telegram;
    }

    @GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false, name = "error") String error,
            @RequestParam(required = false, name = "error_description") String errorDescription) {

        long chatId = oauth.parseChatIdFromState(state);
        try {
            if (error != null) {
                String msg = "Login ERROR: " + error + (errorDescription != null ? " - " + errorDescription : "");
                if (chatId > 0)
                    telegram.sendMessage(chatId, msg);
                return "<h3>" + msg + "</h3>";
            }
            if (code == null) {
                if (chatId > 0)
                    telegram.sendMessage(chatId, "Login ERROR: missing authorization code");
                return "<h3>Missing authorization code</h3>";
            }
            Map tokens = oauth.exchangeCodeForTokens(code, state);
            String summary = "Login OK ✅\n" + oauth.summarizeTokens(tokens);
            if (chatId > 0)
                telegram.sendMessage(chatId, summary);

            return """
                    <html><body>
                    <h3>Login successful. You can return to Telegram.</h3>
                    <pre>""" + summary.replace("&", "&amp;").replace("<", "&lt;") + "</pre>" +
                    "</body></html>";
        } catch (Exception e) {
            String msg = "Login ERROR: " + e.getMessage();
            if (chatId > 0)
                telegram.sendMessage(chatId, msg);
            return "<h3>" + msg + "</h3>";
        }
    }
}
