package com.selfservice.telegrambot.controller;

import com.selfservice.application.auth.OAuthSessionService;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.FindUserResult;
import com.selfservice.application.service.FindUserService;
import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.telegrambot.service.UserSessionService;
import com.selfservice.whatsapp.service.WhatsappService;
import com.selfservice.whatsapp.service.WhatsappSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class OAuthCallbackController {
    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackController.class);

    private final OAuthSessionService oauth;
    private final TelegramService telegram;
    private final UserSessionService sessions;
    private final FindUserService findUserService;
    private final WhatsappService whatsappService;
    private final WhatsappSessionService whatsappSessions;

    public OAuthCallbackController(OAuthSessionService oauth,
                                   TelegramService telegram,
                                   UserSessionService sessions,
                                   FindUserService findUserService,
                                   WhatsappService whatsappService,
                                   WhatsappSessionService whatsappSessions) {
        this.oauth = oauth;
        this.telegram = telegram;
        this.sessions = sessions;
        this.findUserService = findUserService;
        this.whatsappService = whatsappService;
        this.whatsappSessions = whatsappSessions;
    }

    @GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false, name = "error") String error,
                           @RequestParam(required = false, name = "error_description") String errorDescription) {

        String sessionKey = oauth.parseSessionKeyFromState(state);
        long chatId = oauth.parseChatIdFromState(state);
        boolean whatsappUser = sessionKey != null && sessionKey.startsWith("wa-");
        String whatsappChatId = whatsappUser ? sessionKey.substring(3) : null;
        try {
            if (error != null) {
                String msg = "Login ERROR: " + error + (errorDescription != null ? " - " + errorDescription : "");
                log.error("Self-service login failed for session {}: {}", sessionKey, msg);
                if (chatId > 0) {
                    telegram.sendMessageWithKey(chatId, "LoginFailed");
                    telegram.sendLoginMenu(chatId, oauth.buildAuthUrl(chatId));
                }
                if (whatsappUser && whatsappChatId != null) {
                    whatsappService.sendText(whatsappChatId, msg);
                }
                return "<h3>" + msg + "</h3>";
            }
            if (code == null) {
                String msg = "Login ERROR: missing authorization code";
                log.error("Self-service login failed for session {}: {}", sessionKey, msg);
                if (chatId > 0) {
                    telegram.sendMessageWithKey(chatId, "LoginFailed");
                    telegram.sendLoginMenu(chatId, oauth.buildAuthUrl(chatId));
                }
                if (whatsappUser && whatsappChatId != null) {
                    whatsappService.sendText(whatsappChatId, msg);
                }
                return "<h3>Missing authorization code</h3>";
            }

            // 1) Exchange code -> tokens (for PKADMINJ_SELF realm config)
            Map tokens = oauth.exchangeCodeForTokens(code, state);

            // 2) Summarize for user
            String tokenSummary = oauth.summarizeTokens(tokens);
            if (tokenSummary == null) {
                tokenSummary = "No token body.";
            }
            log.info("Login token summary for session {} (chatId={}):\n{}", sessionKey, chatId, tokenSummary);

            // 3) Store token for this chat
            Object at = tokens.get("access_token");
            Object exp = tokens.get("expires_in");
            Object rt = tokens.get("refresh_token");
            Object id = tokens.get("id_token");
            if (chatId > 0 && at instanceof String) {
                long expSecs = (exp instanceof Number) ? ((Number) exp).longValue() : 300L;
                sessions.save(chatId,
                        (String) at,
                        rt instanceof String ? (String) rt : null,
                        id instanceof String ? (String) id : null,
                        expSecs);
            }
            if (whatsappUser && sessionKey != null && at instanceof String) {
                long expSecs = (exp instanceof Number) ? ((Number) exp).longValue() : 300L;
                whatsappSessions.save(sessionKey,
                        (String) at,
                        rt instanceof String ? (String) rt : null,
                        id instanceof String ? (String) id : null,
                        expSecs);
            }

            // 4) Immediately call APIMAN with the user token
            FindUserResult findUserResult = (at instanceof String)
                    ? findUserService.fetchAccountNumbers((String) at)
                    : new FindUserResult(false, "No access_token to call APIMAN.", java.util.List.of(), null);

            if (findUserResult.summary() != null) {
                log.info("findUser summary: {}", findUserResult.summary());
            }

            String accountListMessage;
            List<AccountSummary> accounts = findUserResult.accounts();
            if (findUserResult.success()) {
                if (chatId > 0) {
                    sessions.saveAccounts(chatId, accounts);
                }
                if (whatsappUser && sessionKey != null) {
                    whatsappSessions.saveAccounts(sessionKey, accounts);
                }
                String noAccountsMessage = (chatId > 0)
                        ? telegram.translate(chatId, "NoBillingAccountsFound")
                        : "No billing accounts were found.";
                accountListMessage = accounts.isEmpty()
                        ? noAccountsMessage
                        : accounts.stream()
                        .map(a -> a.accountId() + " - " + a.truncatedName())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse(noAccountsMessage);
            } else {
                accountListMessage = findUserResult.summary();
            }

            // 5) DM Telegram with both
            if (chatId > 0) {
                String greeting = (findUserResult.givenName() != null && !findUserResult.givenName().isBlank())
                        ? telegram.format(chatId, "LoginGreeting", findUserResult.givenName())
                        : null;

                if (findUserResult.success()) {
                    if (accounts.isEmpty()) {
                        sessions.clearSelectedAccount(chatId);
                        String noAccountsMessage = telegram.translate(chatId, "NoBillingAccountsFound");
                        if (greeting != null && !greeting.isBlank()) {
                            telegram.sendMessage(chatId, greeting.strip() + "\n\n" + noAccountsMessage);
                        } else {
                            telegram.sendMessage(chatId, noAccountsMessage);
                        }
                    } else if (accounts.size() == 1) {
                        var onlyAccount = accounts.get(0);
                        sessions.selectAccount(chatId, onlyAccount);
                        telegram.sendLoggedInMenu(chatId, onlyAccount, false, greeting);
                    } else {
                        sessions.clearSelectedAccount(chatId);
                        telegram.sendAccountPage(chatId, accounts, 0, greeting);
                    }
                } else {
                    sessions.clearSelectedAccount(chatId);
                    telegram.sendMessage(chatId, accountListMessage);
                }
            }

            if (whatsappUser && whatsappChatId != null) {
                String greeting = (findUserResult.givenName() != null && !findUserResult.givenName().isBlank())
                        ? "Welcome " + findUserResult.givenName() + "!"
                        : null;

                StringBuilder waMessage = new StringBuilder();
                if (findUserResult.success()) {
                    if (greeting != null) {
                        waMessage.append(greeting).append("\n\n");
                    }
                    waMessage.append("Login OK ✅\nBearer token:\n");
                    waMessage.append(at instanceof String ? (String) at : "<missing token>");
                    waMessage.append("\n\nAPIMAN findUser summary:\n").append(accountListMessage);
                } else {
                    waMessage.append("Login failed 😞\n").append(accountListMessage);
                }

                whatsappService.sendText(whatsappChatId, waMessage.toString());
            }

            return """
                   <html><body>
                   <h3>Login successful. You can return to your chat.</h3>
                   <pre>""" + (("Login OK ✅\n" + tokenSummary) + "\n\nAPIMAN findUser summary:\n" + accountListMessage)
                        .replace("&","&amp;").replace("<","&lt;") + "</pre></body></html>";
        } catch (Exception e) {
            String msg = "Login ERROR: " + e.getMessage();
            log.error("Self-service login failed for session {}", sessionKey, e);
            if (chatId > 0) {
                telegram.sendMessageWithKey(chatId, "LoginFailed");
                telegram.sendLoginMenu(chatId, oauth.buildAuthUrl(chatId));
            }
            if (whatsappUser && whatsappChatId != null) {
                whatsappService.sendText(whatsappChatId, msg);
            }
            return "<h3>" + msg + "</h3>";
        }
    }
}
