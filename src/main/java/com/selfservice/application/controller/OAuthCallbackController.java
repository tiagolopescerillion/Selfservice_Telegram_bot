package com.selfservice.application.controller;

import com.selfservice.application.auth.OAuthSessionService;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.FindUserResult;
import com.selfservice.application.config.UxProperties;
import com.selfservice.application.config.ConnectorsProperties;
import com.selfservice.application.service.FindUserService;
import com.selfservice.application.service.ImpersonationService;
import com.selfservice.application.service.AccountBalanceService;
import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.telegrambot.service.UserSessionService;
import com.selfservice.whatsapp.service.WhatsappService;
import com.selfservice.whatsapp.service.WhatsappSessionService;
import com.selfservice.application.service.OperationsMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@RestController
public class OAuthCallbackController {
    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackController.class);

    private final OAuthSessionService oauth;
    private final TelegramService telegram;
    private final UserSessionService sessions;
    private final com.selfservice.application.service.ProductService productService;
    private final FindUserService findUserService;
    private final ImpersonationService impersonationService;
    private final WhatsappService whatsappService;
    private final WhatsappSessionService whatsappSessions;
    private final OperationsMonitoringService monitoringService;
    private final UxProperties uxProperties;
    private final ConnectorsProperties connectorsProperties;
    private final AccountBalanceService accountBalanceService;

    public OAuthCallbackController(OAuthSessionService oauth,
                                   TelegramService telegram,
                                   UserSessionService sessions,
                                   com.selfservice.application.service.ProductService productService,
                                   FindUserService findUserService,
                                   ImpersonationService impersonationService,
                                   WhatsappService whatsappService,
                                   WhatsappSessionService whatsappSessions,
                                   OperationsMonitoringService monitoringService,
                                   UxProperties uxProperties,
                                   ConnectorsProperties connectorsProperties,
                                   AccountBalanceService accountBalanceService) {
        this.oauth = oauth;
        this.telegram = telegram;
        this.sessions = sessions;
        this.productService = productService;
        this.findUserService = findUserService;
        this.impersonationService = impersonationService;
        this.whatsappService = whatsappService;
        this.whatsappSessions = whatsappSessions;
        this.monitoringService = monitoringService;
        this.uxProperties = uxProperties;
        this.connectorsProperties = connectorsProperties;
        this.accountBalanceService = accountBalanceService;
    }

    @GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false, name = "error") String error,
                           @RequestParam(required = false, name = "error_description") String errorDescription) {

        String channel = oauth.parseChannelFromState(state);
        String sessionKey = oauth.parseSessionKeyFromState(state);
        long chatId = oauth.parseChatIdFromState(state);
        boolean whatsappUser = (channel != null && channel.equalsIgnoreCase("whatsapp"))
                || (sessionKey != null && sessionKey.startsWith("wa-"));
        String whatsappChatId = whatsappUser ? sessionKey.substring(3) : null;

        if (whatsappUser && !connectorsProperties.isWhatsappEnabled()) {
            log.warn("Ignoring OAuth callback for disabled WhatsApp connector and session {}", sessionKey);
            return "<h3>WhatsApp connector is disabled.</h3>";
        }

        if (!whatsappUser && !connectorsProperties.isTelegramEnabled()) {
            log.warn("Ignoring OAuth callback for disabled Telegram connector and session {}", sessionKey);
            return "<h3>Telegram connector is disabled.</h3>";
        }
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
            String exchangeId = (at instanceof String)
                    ? impersonationService.initiate((String) at)
                    : null;
            boolean telegramOptIn = chatId > 0 && sessions.isOptedIn(chatId);
            boolean whatsappOptIn = whatsappUser && whatsappChatId != null && whatsappSessions.isOptedIn(whatsappChatId);
            if (chatId > 0 && at instanceof String) {
                long expSecs = (exp instanceof Number) ? ((Number) exp).longValue() : 300L;
                sessions.save(chatId,
                        (String) at,
                        rt instanceof String ? (String) rt : null,
                        id instanceof String ? (String) id : null,
                        expSecs,
                        exchangeId);
                monitoringService.markLoggedIn("Telegram", Long.toString(chatId), null, telegramOptIn);
            }
            if (whatsappUser && whatsappChatId != null && at instanceof String) {
                long expSecs = (exp instanceof Number) ? ((Number) exp).longValue() : 300L;
                whatsappSessions.save(whatsappChatId,
                        (String) at,
                        rt instanceof String ? (String) rt : null,
                        id instanceof String ? (String) id : null,
                        expSecs,
                        exchangeId);
                monitoringService.markLoggedIn("WhatsApp", whatsappChatId, null, whatsappOptIn);
            }

            // 4) Immediately call APIMAN with the user token
            FindUserResult findUserResult = (at instanceof String)
                    ? findUserService.fetchAccountNumbers((String) at)
                    : new FindUserResult(false, "No access_token to call APIMAN.", java.util.List.of(), null);

            if (findUserResult.summary() != null) {
                log.info("findUser summary: {}", findUserResult.summary());
            }

            String displayName = findUserResult.givenName();
            if (chatId > 0) {
                monitoringService.markLoggedIn("Telegram", Long.toString(chatId), displayName, telegramOptIn);
            }
            if (whatsappUser && whatsappChatId != null) {
                monitoringService.markLoggedIn("WhatsApp", whatsappChatId, displayName, whatsappOptIn);
            }

            String accountListMessage;
            List<AccountSummary> accounts = findUserResult.accounts();
            boolean setContextOnLogin = uxProperties.isSetContext();
            if (findUserResult.success()) {
                if (chatId > 0) {
                    sessions.saveAccounts(chatId, accounts);
                }
                if (whatsappUser && whatsappChatId != null) {
                    whatsappSessions.saveAccounts(whatsappChatId, accounts);
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
                    if (!setContextOnLogin) {
                        sessions.clearSelectedAccount(chatId);
                        telegram.sendLoggedInMenu(chatId, null, false, greeting);
                    } else if (accounts.isEmpty()) {
                        sessions.clearSelectedAccount(chatId);
                        String noAccountsMessage = telegram.translate(chatId, "NoBillingAccountsFound");
                        if (greeting != null && !greeting.isBlank()) {
                            telegram.sendMessage(chatId, greeting.strip() + "\n\n" + noAccountsMessage);
                        } else {
                            telegram.sendMessage(chatId, noAccountsMessage);
                        }
                    } else {
                        // Select first account and auto-select first service if available; always show combined card
                        var firstAccount = accounts.get(0);
                        sessions.selectAccount(chatId, firstAccount);
                        com.selfservice.application.dto.ServiceSummary firstService = null;
                        try {
                            String accessToken = (at instanceof String) ? (String) at : null;
                            if (accessToken != null) {
                                var servicesResult = productService.getMainServices(accessToken, firstAccount.accountId());
                                if (!servicesResult.hasError() && servicesResult.services() != null && !servicesResult.services().isEmpty()) {
                                    sessions.saveServices(chatId, servicesResult.services());
                                    firstService = servicesResult.services().get(0);
                                    sessions.selectService(chatId, firstService);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        telegram.sendAccountServiceCard(chatId, firstAccount, firstService, accounts.size() > 1, greeting);
                    }
                } else {
                    sessions.clearSelectedAccount(chatId);
                    telegram.sendMessage(chatId, accountListMessage);
                }
            }

            if (whatsappUser && whatsappChatId != null) {
                String greeting = (findUserResult.givenName() != null && !findUserResult.givenName().isBlank())
                        ? "Hello " + findUserResult.givenName()
                        : null;

                if (findUserResult.success()) {
                    if (!setContextOnLogin) {
                        whatsappSessions.clearSelectedAccount(whatsappChatId);
                        whatsappService.sendLoggedInMenu(whatsappChatId, null, false, null);
                    } else if (accounts.isEmpty()) {
                        whatsappSessions.clearSelectedAccount(whatsappChatId);
                        whatsappService.sendAccountServiceCard(whatsappChatId, null, null, false, greeting);
                    } else {
                        // Always select first account and auto-select first service; show combined card
                        var firstAccount = accounts.get(0);
                        whatsappSessions.selectAccount(whatsappChatId, firstAccount);
                        com.selfservice.application.dto.ServiceSummary firstService = null;
                        try {
                            String accessToken = (at instanceof String) ? (String) at : null;
                            if (accessToken != null) {
                                var servicesResult = productService.getMainServices(accessToken, firstAccount.accountId());
                                if (!servicesResult.hasError() && servicesResult.services() != null && !servicesResult.services().isEmpty()) {
                                    whatsappSessions.saveServices(whatsappChatId, servicesResult.services());
                                    firstService = servicesResult.services().get(0);
                                    whatsappSessions.selectService(whatsappChatId, firstService);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        var balance = accountBalanceService.lookup((String) at, firstAccount.accountId());
                        if (balance.hasDueBalance()) {
                            whatsappService.sendAccountBalanceAlert(whatsappChatId, firstAccount, firstService, greeting,
                                    balance.current(), balance.overdue());
                        } else {
                            // Send combined account+service card including greeting in the card header
                            whatsappService.sendAccountServiceCard(whatsappChatId, firstAccount, firstService, accounts.size() > 1, greeting);
                        }
                    }
                } else {
                    whatsappService.sendText(whatsappChatId, accountListMessage);
                }
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

