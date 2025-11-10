package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class OAuthCallbackController {
    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackController.class);

    private final OAuthLoginService oauth;
    private final TelegramService telegram;
    private final UserSessionService sessions;
    private final ApimanApiService apiman;

    public OAuthCallbackController(OAuthLoginService oauth,
                                   TelegramService telegram,
                                   UserSessionService sessions,
                                   ApimanApiService apiman) {
        this.oauth = oauth;
        this.telegram = telegram;
        this.sessions = sessions;
        this.apiman = apiman;
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
                if (chatId > 0) telegram.sendMessage(chatId, msg);
                return "<h3>" + msg + "</h3>";
            }
            if (code == null) {
                if (chatId > 0) telegram.sendMessage(chatId, "Login ERROR: missing authorization code");
                return "<h3>Missing authorization code</h3>";
            }

            // 1) Exchange code -> tokens (for PKADMINJ_SELF realm config)
            Map tokens = oauth.exchangeCodeForTokens(code, state);

            // 2) Summarize for user
            String summary = "Login OK ✅\n" + oauth.summarizeTokens(tokens);

            // 3) Store token for this chat
            Object at = tokens.get("access_token");
            Object exp = tokens.get("expires_in");
            if (chatId > 0 && at instanceof String) {
                long expSecs = (exp instanceof Number) ? ((Number) exp).longValue() : 300L;
                sessions.save(chatId, (String) at, expSecs);
            }

            // 4) Immediately call APIMAN with the user token
            String apiResult = (at instanceof String)
                    ? apiman.callWithBearer((String) at)
                    : "No access_token to call APIMAN.";
            // 5) DM Telegram with both
            if (chatId > 0) {
                telegram.sendMessage(chatId, summary + "\n\nAPIMAN result:\n" + apiResult);
            }

            return """
                   <html><body>
                   <h3>Login successful. You can return to Telegram.</h3>
                   <pre>""" + (summary + "\n\n" + apiResult)
                        .replace("&","&amp;").replace("<","&lt;") + "</pre></body></html>";
        } catch (Exception e) {
            String msg = "Login ERROR: " + e.getMessage();
            if (chatId > 0) telegram.sendMessage(chatId, msg);
            return "<h3>" + msg + "</h3>";
        }
    }
}
