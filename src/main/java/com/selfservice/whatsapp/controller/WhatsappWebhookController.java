package com.selfservice.whatsapp.controller;

import com.selfservice.application.auth.KeycloakAuthService;
import com.selfservice.application.auth.OAuthSessionService;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.ServiceListResult;
import com.selfservice.application.dto.ServiceSummary;
import com.selfservice.application.dto.TroubleTicketListResult;
import com.selfservice.application.service.ExternalApiService;
import com.selfservice.application.service.MainServiceCatalogService;
import com.selfservice.application.service.TroubleTicketService;
import com.selfservice.telegrambot.config.menu.BusinessMenuItem;
import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.whatsapp.service.WhatsappService;
import com.selfservice.whatsapp.service.WhatsappSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);

    private final WhatsappService whatsappService;
    private final OAuthSessionService oauthSessionService;
    private final WhatsappSessionService sessionService;
    private final KeycloakAuthService keycloakAuthService;
    private final ExternalApiService externalApiService;
    private final MainServiceCatalogService mainServiceCatalogService;
    private final TroubleTicketService troubleTicketService;
    private final String verifyToken;

    public WhatsappWebhookController(
            WhatsappService whatsappService,
            OAuthSessionService oauthSessionService,
            WhatsappSessionService sessionService,
            KeycloakAuthService keycloakAuthService,
            ExternalApiService externalApiService,
            MainServiceCatalogService mainServiceCatalogService,
            TroubleTicketService troubleTicketService,
            @Value("${whatsapp.verify-token}") String verifyToken) {
        this.whatsappService = whatsappService;
        this.oauthSessionService = oauthSessionService;
        this.sessionService = sessionService;
        this.keycloakAuthService = keycloakAuthService;
        this.externalApiService = externalApiService;
        this.mainServiceCatalogService = mainServiceCatalogService;
        this.troubleTicketService = troubleTicketService;
        this.verifyToken = Objects.requireNonNull(verifyToken, "whatsapp.verify-token must be set");
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        log.info("WhatsApp webhook verification request mode={} token={}", mode, token);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge == null ? "" : challenge);
        }

        return ResponseEntity.status(403).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<Void> onEvent(@RequestBody Map<String, Object> payload) {
        log.info("Incoming WhatsApp webhook: {}", payload);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
        if (entries == null) {
            return ResponseEntity.ok().build();
        }

        for (Map<String, Object> entry : entries) {
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
            if (changes == null) {
                continue;
            }

            for (Map<String, Object> change : changes) {
                Map<String, Object> value = (Map<String, Object>) change.get("value");
                if (value == null) {
                    continue;
                }

                List<Map<String, Object>> messages =
                        (List<Map<String, Object>>) value.get("messages");
                if (messages == null) {
                    continue;
                }

                for (Map<String, Object> message : messages) {
                    String from = (String) message.get("from");
                    if (from == null || from.isBlank()) {
                        continue;
                    }

                    String type = (String) message.get("type");
                    if (!"text".equals(type)) {
                        dispatchFallback(from);
                        continue;
                    }

                    Map<String, Object> text = (Map<String, Object>) message.get("text");
                    String body = text == null ? null : (String) text.get("body");
                    if (body == null) {
                        dispatchFallback(from);
                        continue;
                    }

                    handleCommand(from, body.trim());
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    private void handleCommand(String from, String body) {
        String sessionKey = "wa-" + from;
        String userId = from;
        String lower = body.toLowerCase();
        boolean awaitingLanguage = sessionService.isAwaitingLanguageSelection(userId);
        String token = sessionService.getValidAccessToken(userId);
        boolean hasValidToken = token != null;

        if (lower.equals(WhatsappService.COMMAND_MENU)) {
            sessionService.setAwaitingLanguageSelection(userId, false);
            if (!hasValidToken) {
                sendLoginPrompt(from, sessionKey);
                return;
            }
            sendBusinessMenu(from, userId);
            return;
        }

        if (awaitingLanguage && tryHandleLanguageSelection(sessionKey, userId, lower, hasValidToken)) {
            return;
        }

        if (lower.startsWith("lang")) {
            if (hasValidToken) {
                sendBusinessMenu(from, userId);
                return;
            }
            sessionService.setAwaitingLanguageSelection(userId, true);
            handleLanguageChange(sessionKey, userId, lower, hasValidToken);
            return;
        }

        if (lower.equals("1") || lower.contains(TelegramService.CALLBACK_SELF_SERVICE_LOGIN.toLowerCase()) ||
                lower.contains("login")) {
            if (!hasValidToken) {
                sendLoginPrompt(from, sessionKey);
                return;
            }
            // Logged-in users should treat numeric options as business menu selections, not login shortcuts.
        }

        if (lower.equals("2") || lower.contains(TelegramService.CALLBACK_DIRECT_LOGIN.toLowerCase())) {
            if (!hasValidToken) {
                String authMessage;
                String accessToken = null;
                try {
                    accessToken = keycloakAuthService.getAccessToken();
                    authMessage = whatsappService.translate(from, "AuthOk");
                } catch (Exception e) {
                    authMessage = whatsappService.format(from, "AuthError", e.getMessage());
                }

                String apiResponse = whatsappService.translate(from, "NoApiResponse");
                if (accessToken != null) {
                    apiResponse = externalApiService.callTroubleTicketApi(accessToken);
                }
                whatsappService.sendText(from, authMessage + "\n\n" +
                        whatsappService.format(from, "ExternalApiResult", apiResponse));
                sendLoginPrompt(from, sessionKey);
                return;
            }
        }

        if (!hasValidToken) {
            if (lower.equals("3") || lower.equals(WhatsappService.COMMAND_CHANGE_LANGUAGE)) {
                sessionService.setAwaitingLanguageSelection(userId, true);
                whatsappService.sendLanguageMenu(from);
                return;
            }
            sendLoginPrompt(from, sessionKey);
            return;
        }

        if (lower.startsWith("account")) {
            handleAccountSelection(sessionKey, userId, lower);
            return;
        }

        if (lower.startsWith("more accounts")) {
            int offset = parseIndex(lower.replace("more accounts", "").trim());
            whatsappService.sendAccountPage(from, sessionService.getAccounts(userId), offset);
            return;
        }

        if (lower.startsWith("service")) {
            handleServiceSelection(userId, from, lower);
            return;
        }

        if (lower.startsWith("more services")) {
            int offset = parseIndex(lower.replace("more services", "").trim());
            whatsappService.sendServicePage(from, sessionService.getServices(userId), offset);
            return;
        }

        if (lower.startsWith("ticket")) {
            String id = lower.replace("ticket", "").trim();
            if (id.isEmpty()) {
                whatsappService.sendText(from, whatsappService.translate(from, "TicketNoLongerAvailable"));
            } else {
                whatsappService.sendText(from, whatsappService.format(from, "TicketSelected", id));
            }
            AccountSummary selected = sessionService.getSelectedAccount(userId);
            whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
            return;
        }

        if (lower.startsWith("more tickets")) {
            int offset = parseIndex(lower.replace("more tickets", "").trim());
            whatsappService.sendTroubleTicketPage(from, sessionService.getTroubleTickets(userId), offset);
            return;
        }

        if (lower.equals(WhatsappService.COMMAND_LOGOUT)) {
            String refreshToken = sessionService.getRefreshToken(userId);
            String idToken = sessionService.getIdToken(userId);
            try {
                oauthSessionService.logout(refreshToken, idToken);
            } catch (Exception ex) {
                log.warn("Logout failed for WhatsApp user {}", sessionKey, ex);
            }
            sessionService.clearSession(userId);
            whatsappService.sendText(from, whatsappService.translate(from, "LoggedOutMessage"));
            sendLoginPrompt(from, sessionKey);
            return;
        }

        if (lower.equals(WhatsappService.COMMAND_CHANGE_ACCOUNT) || lower.equals("c")) {
            List<AccountSummary> accounts = sessionService.getAccounts(userId);
            if (accounts.isEmpty()) {
                sessionService.clearSelectedAccount(userId);
                whatsappService.sendText(from, whatsappService.translate(from, "NoStoredAccounts"));
                sendLoginPrompt(from, sessionKey);
            } else {
                sessionService.clearSelectedAccount(userId);
                whatsappService.sendText(from, whatsappService.translate(from, "ChooseAccountToContinue"));
                whatsappService.sendAccountPage(from, accounts, 0);
            }
            return;
        }

        if (lower.equals(WhatsappService.COMMAND_HOME)) {
            whatsappService.goHomeBusinessMenu(userId);
            AccountSummary selected = sessionService.getSelectedAccount(userId);
            whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
            return;
        }

        if (lower.equals(WhatsappService.COMMAND_UP)) {
            whatsappService.goUpBusinessMenu(userId);
            AccountSummary selected = sessionService.getSelectedAccount(userId);
            whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
            return;
        }

        List<BusinessMenuItem> menuItems = whatsappService.currentMenuItems(userId);
        int depth = whatsappService.currentMenuDepth(userId);
        boolean showChangeAccountOption = sessionService.getAccounts(userId).size() > 1;
        int numeric = parseIndex(lower);
        if (numeric >= 1 && numeric <= menuItems.size()) {
            BusinessMenuItem item = menuItems.get(numeric - 1);
            if (item.isSubMenu()) {
                if (!whatsappService.goToBusinessMenu(userId, item.submenuId())) {
                    whatsappService.sendText(from, whatsappService.translate(from, "BusinessMenuUnavailable"));
                }
                AccountSummary selected = sessionService.getSelectedAccount(userId);
                whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
                return;
            }

            switch (item.function()) {
                case TelegramService.CALLBACK_HELLO_WORLD -> {
                    if (ensureAccountSelected(sessionKey, userId)) {
                        AccountSummary selected = sessionService.getSelectedAccount(userId);
                        whatsappService.sendText(from, whatsappService.translate(from, "HelloWorldMessage"));
                        whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
                    }
                }
                case TelegramService.CALLBACK_HELLO_CERILLION -> {
                    if (ensureAccountSelected(sessionKey, userId)) {
                        AccountSummary selected = sessionService.getSelectedAccount(userId);
                        whatsappService.sendText(from, whatsappService.translate(from, "HelloCerillionMessage"));
                        whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
                    }
                }
                case TelegramService.CALLBACK_TROUBLE_TICKET -> {
                    if (ensureAccountSelected(sessionKey, userId)) {
                        AccountSummary selected = sessionService.getSelectedAccount(userId);
                        String ticketInfo = troubleTicketService.callTroubleTicket(token);
                        whatsappService.sendText(from,
                                whatsappService.format(from, "TroubleTicketInformation", ticketInfo));
                        whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
                    }
                }
                case TelegramService.CALLBACK_SELECT_SERVICE -> handleServiceLookup(sessionKey, userId, token);
                case TelegramService.CALLBACK_MY_ISSUES -> handleTroubleTickets(sessionKey, userId, token);
                default -> sendBusinessMenu(from, userId);
            }
            return;
        }

        int actionIndex = menuItems.size();
        if (depth >= 1) {
            actionIndex++;
            if (numeric == actionIndex) {
                whatsappService.goHomeBusinessMenu(userId);
                AccountSummary selected = sessionService.getSelectedAccount(userId);
                whatsappService.sendLoggedInMenu(from, selected, showChangeAccountOption);
                return;
            }
            if (depth >= 2) {
                actionIndex++;
                if (numeric == actionIndex) {
                    whatsappService.goUpBusinessMenu(userId);
                    AccountSummary selected = sessionService.getSelectedAccount(userId);
                    whatsappService.sendLoggedInMenu(from, selected, showChangeAccountOption);
                    return;
                }
            }
        }
        if (showChangeAccountOption) {
            actionIndex++;
            if (numeric == actionIndex) {
                List<AccountSummary> accounts = sessionService.getAccounts(userId);
                if (accounts.isEmpty()) {
                    sessionService.clearSelectedAccount(userId);
                    whatsappService.sendText(from, whatsappService.translate(from, "NoStoredAccounts"));
                    sendLoginPrompt(from, sessionKey);
                } else {
                    sessionService.clearSelectedAccount(userId);
                    whatsappService.sendText(from, whatsappService.translate(from, "ChooseAccountToContinue"));
                    whatsappService.sendAccountPage(from, accounts, 0);
                }
                return;
            }
        }

        actionIndex++;
        if (numeric == actionIndex) {
            String refreshToken = sessionService.getRefreshToken(userId);
            String idToken = sessionService.getIdToken(userId);
            try {
                oauthSessionService.logout(refreshToken, idToken);
            } catch (Exception ex) {
                log.warn("Logout failed for WhatsApp user {}", sessionKey, ex);
            }
            sessionService.clearSession(userId);
            whatsappService.sendText(from, whatsappService.translate(from, "LoggedOutMessage"));
            sendLoginPrompt(from, sessionKey);
            return;
        }

        sendBusinessMenu(from, userId);
    }

    private void handleLanguageChange(String sessionKey, String userId, String lower, boolean hasValidToken) {
        if (lower.equals("lang") || lower.equals("lang?")) {
            whatsappService.sendLanguageMenu(userId);
            return;
        }
        String[] parts = lower.split("\\s+");
        if (parts.length < 2) {
            whatsappService.sendLanguageMenu(userId);
            return;
        }
        tryHandleLanguageSelection(sessionKey, userId, parts[1], hasValidToken);
    }

    private boolean tryHandleLanguageSelection(String sessionKey, String userId, String selection, boolean hasValidToken) {
        String code = mapLanguageCode(selection);
        if (code == null || code.isBlank()) {
            whatsappService.sendLanguageMenu(userId);
            return true;
        }
        if (!whatsappService.isSupportedLanguage(code)) {
            whatsappService.sendText(userId, whatsappService.translate(userId, "LanguageNotSupported"));
            whatsappService.sendLanguageMenu(userId);
            return true;
        }
        sessionService.setAwaitingLanguageSelection(userId, false);
        sessionService.setLanguage(userId, code);
        String labelKey = switch (code) {
            case "fr" -> "LanguageFrench";
            case "pt" -> "LanguagePortuguese";
            case "ru" -> "LanguageRussian";
            case "en" -> "LanguageEnglish";
            default -> "LanguageEnglish";
        };
        whatsappService.sendText(userId,
                whatsappService.format(userId, "LanguageUpdated", whatsappService.translate(userId, labelKey)));
        if (hasValidToken && ensureAccountSelected(sessionKey, userId)) {
            sendBusinessMenu(userId, userId);
        } else {
            sendLoginPrompt(userId, sessionKey);
        }
        return true;
    }

    private String mapLanguageCode(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return switch (input.trim()) {
            case "1", "en" -> "en";
            case "2", "fr" -> "fr";
            case "3", "pt" -> "pt";
            case "4", "ru" -> "ru";
            default -> input.trim();
        };
    }

    private void handleAccountSelection(String sessionKey, String userId, String lower) {
        List<AccountSummary> accounts = sessionService.getAccounts(userId);
        int index = parseIndex(lower.replace("account", "").trim()) - 1;
        if (accounts.isEmpty() || index < 0 || index >= accounts.size()) {
            whatsappService.sendText(userId, whatsappService.translate(userId, "AccountSelectionExpired"));
            whatsappService.sendAccountPage(userId, accounts, 0);
            return;
        }
        AccountSummary selected = accounts.get(index);
        sessionService.selectAccount(userId, selected);
        whatsappService.sendText(userId, whatsappService.format(userId, "SelectedAccount", selected.displayLabel()));
        whatsappService.sendLoggedInMenu(userId, selected, accounts.size() > 1);
    }

    private void handleServiceSelection(String userId, String from, String lower) {
        List<ServiceSummary> services = sessionService.getServices(userId);
        int index = parseIndex(lower.replace("service", "").trim()) - 1;
        if (services.isEmpty() || index < 0 || index >= services.size()) {
            whatsappService.sendText(from, whatsappService.translate(userId, "ServiceNoLongerAvailable"));
        } else {
            ServiceSummary selectedService = services.get(index);
            String name = (selectedService.productName() == null || selectedService.productName().isBlank())
                    ? whatsappService.translate(userId, "UnknownService")
                    : selectedService.productName().strip();
            String number = (selectedService.accessNumber() == null || selectedService.accessNumber().isBlank())
                    ? whatsappService.translate(userId, "NoAccessNumber")
                    : selectedService.accessNumber().strip();
            whatsappService.sendText(from, whatsappService.format(userId, "ServiceSelected", name, number));
        }
        sendBusinessMenu(from, userId);
    }

    private void handleServiceLookup(String sessionKey, String userId, String token) {
        if (!ensureAccountSelected(sessionKey, userId)) {
            return;
        }
        AccountSummary selected = sessionService.getSelectedAccount(userId);
        ServiceListResult services = mainServiceCatalogService.getMainServices(token, selected.accountId());
        if (services.hasError()) {
            sessionService.clearServices(userId);
            whatsappService.sendText(userId,
                    whatsappService.format(userId, "UnableToRetrieveServices", services.errorMessage()));
            whatsappService.sendLoggedInMenu(userId, selected, sessionService.getAccounts(userId).size() > 1);
        } else if (services.services().isEmpty()) {
            sessionService.clearServices(userId);
            whatsappService.sendText(userId, whatsappService.format(userId, "NoServicesForAccount", selected.accountId()));
            whatsappService.sendLoggedInMenu(userId, selected, sessionService.getAccounts(userId).size() > 1);
        } else {
            sessionService.saveServices(userId, services.services());
            whatsappService.sendServicePage(userId, services.services(), 0);
        }
    }

    private void handleTroubleTickets(String sessionKey, String userId, String token) {
        if (!ensureAccountSelected(sessionKey, userId)) {
            return;
        }
        AccountSummary selected = sessionService.getSelectedAccount(userId);
        TroubleTicketListResult result = troubleTicketService.getTroubleTicketsByAccountId(token, selected.accountId());
        if (result.hasError()) {
            sessionService.clearTroubleTickets(userId);
            whatsappService.sendText(userId,
                    whatsappService.format(userId, "UnableToRetrieveTroubleTickets", result.errorMessage()));
            whatsappService.sendLoggedInMenu(userId, selected, sessionService.getAccounts(userId).size() > 1);
        } else if (result.tickets().isEmpty()) {
            sessionService.clearTroubleTickets(userId);
            whatsappService.sendText(userId,
                    whatsappService.format(userId, "NoTroubleTicketsForAccount", selected.accountId()));
            whatsappService.sendLoggedInMenu(userId, selected, sessionService.getAccounts(userId).size() > 1);
        } else {
            sessionService.saveTroubleTickets(userId, result.tickets());
            whatsappService.sendTroubleTicketPage(userId, result.tickets(), 0);
        }
    }

    private boolean ensureAccountSelected(String sessionKey, String userId) {
        AccountSummary selected = sessionService.getSelectedAccount(userId);
        if (selected != null) {
            return true;
        }
        List<AccountSummary> accounts = sessionService.getAccounts(userId);
        if (accounts.isEmpty()) {
            whatsappService.sendText(userId, whatsappService.translate(userId, "NoStoredAccounts"));
            sendLoginPrompt(userId, sessionKey);
            return false;
        }
        whatsappService.sendText(userId, whatsappService.translate(userId, "ChooseAccountToContinue"));
        whatsappService.sendAccountPage(userId, accounts, 0);
        return false;
    }

    private void dispatchFallback(String from) {
        String sessionKey = "wa-" + from;
        boolean hasValidToken = sessionService.getValidAccessToken(from) != null;
        if (hasValidToken) {
            sendBusinessMenu(from, from);
        } else {
            sendLoginPrompt(from, sessionKey);
        }
    }

    private void sendBusinessMenu(String to, String userId) {
        if (!ensureAccountSelected("wa-" + userId, userId)) {
            return;
        }
        AccountSummary selected = sessionService.getSelectedAccount(userId);
        whatsappService.sendLoggedInMenu(to, selected, sessionService.getAccounts(userId).size() > 1);
    }

    private void sendLoginPrompt(String to, String sessionKey) {
        whatsappService.sendLoginMenu(to, oauthSessionService.buildAuthUrl(sessionKey));
    }

    private int parseIndex(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception ex) {
            return -1;
        }
    }
}
