package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.FindUserService;
import com.selfservice.telegrambot.service.OAuthLoginService;
import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.telegrambot.service.UserSessionService;
import com.selfservice.telegrambot.service.dto.AccountSummary;
import com.selfservice.telegrambot.service.dto.FindUserResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class OAuthCallbackController {
    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackController.class);

    private final OAuthLoginService oauth;
    private final TelegramService telegram;
    private final UserSessionService sessions;
    private final FindUserService findUserService;

    public OAuthCallbackController(OAuthLoginService oauth,
                                   TelegramService telegram,
                                   UserSessionService sessions,
                                   FindUserService findUserService) {
        this.oauth = oauth;
        this.telegram = telegram;
        this.sessions = sessions;
        this.findUserService = findUserService;
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
                log.error("Self-service login failed for chat {}: {}", chatId, msg);
                if (chatId > 0) {
                    telegram.sendMessageWithKey(chatId, "LoginFailed");
                    telegram.sendLoginMenu(chatId, oauth.buildAuthUrl(chatId));
                }
                return "<h3>" + msg + "</h3>";
            }
            if (code == null) {
                String msg = "Login ERROR: missing authorization code";
                log.error("Self-service login failed for chat {}: {}", chatId, msg);
                if (chatId > 0) {
                    telegram.sendMessageWithKey(chatId, "LoginFailed");
                    telegram.sendLoginMenu(chatId, oauth.buildAuthUrl(chatId));
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
            log.info("Login token summary for chat {}:\n{}", chatId, tokenSummary);

            // 3) Store token for this chat
            Object at = tokens.get("access_token");
            Object exp = tokens.get("expires_in");
            Object rt = tokens.get("refresh_token");
            if (chatId > 0 && at instanceof String) {
                long expSecs = (exp instanceof Number) ? ((Number) exp).longValue() : 300L;
                sessions.save(chatId, (String) at, rt instanceof String ? (String) rt : null, expSecs);
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
                telegram.sendMessageWithKey(chatId, "LoginOkSummary", findUserResult.summary());
                if (findUserResult.givenName() != null && !findUserResult.givenName().isBlank()) {
                    telegram.sendMessageWithKey(chatId, "LoginGreeting", findUserResult.givenName());
                }

                if (findUserResult.success()) {
                    if (accounts.isEmpty()) {
                        sessions.clearSelectedAccount(chatId);
                        telegram.sendMessageWithKey(chatId, "NoBillingAccountsFound");
                    } else if (accounts.size() == 1) {
                        var onlyAccount = accounts.get(0);
                        sessions.selectAccount(chatId, onlyAccount);
                        telegram.sendMessageWithKey(chatId, "SingleAccountSelected", onlyAccount.displayLabel());
                        telegram.sendLoggedInMenu(chatId, onlyAccount, false);
                    } else {
                        sessions.clearSelectedAccount(chatId);
                        telegram.sendMessageWithKey(chatId, "ChooseAccountToContinue");
                        telegram.sendAccountPage(chatId, accounts, 0);
                    }
                } else {
                    sessions.clearSelectedAccount(chatId);
                    telegram.sendMessage(chatId, accountListMessage);
                }
            }

            return """
                   <html><body>
                   <h3>Login successful. You can return to Telegram.</h3>
                   <pre>""" + (("Login OK ✅\n" + tokenSummary) + "\n\nAPIMAN findUser summary:\n" + accountListMessage)
                        .replace("&","&amp;").replace("<","&lt;") + "</pre></body></html>";
        } catch (Exception e) {
            String msg = "Login ERROR: " + e.getMessage();
            log.error("Self-service login failed for chat {}", chatId, e);
            if (chatId > 0) {
                telegram.sendMessageWithKey(chatId, "LoginFailed");
                telegram.sendLoginMenu(chatId, oauth.buildAuthUrl(chatId));
            }
            return "<h3>" + msg + "</h3>";
        }
    }
}
