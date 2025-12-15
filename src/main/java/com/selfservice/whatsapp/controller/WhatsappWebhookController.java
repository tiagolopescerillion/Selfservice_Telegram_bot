package com.selfservice.whatsapp.controller;

import com.selfservice.application.auth.KeycloakAuthService;
import com.selfservice.application.auth.OAuthSessionService;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.InvoiceListResult;
import com.selfservice.application.dto.InvoiceSummary;
import com.selfservice.application.dto.ServiceListResult;
import com.selfservice.application.dto.ServiceSummary;
import com.selfservice.application.dto.TroubleTicketListResult;
import com.selfservice.application.dto.TroubleTicketSummary;
import com.selfservice.application.service.InvoiceService;
import com.selfservice.application.service.ProductService;
import com.selfservice.application.service.TroubleTicketService;
import com.selfservice.application.service.ServiceFunctionExecutor;
import com.selfservice.application.config.menu.BusinessMenuItem;
import com.selfservice.application.config.menu.LoginMenuFunction;
import com.selfservice.application.config.menu.LoginMenuItem;
import com.selfservice.application.config.menu.BusinessMenuConfigurationProvider;
import com.selfservice.application.config.ConnectorsProperties;
import com.selfservice.application.service.OperationsMonitoringService;
import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.whatsapp.service.WhatsappService;
import com.selfservice.whatsapp.service.WhatsappSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final ProductService productService;
    private final InvoiceService invoiceService;
    private final TroubleTicketService troubleTicketService;
    private final String verifyToken;
    private final OperationsMonitoringService monitoringService;
    private final ConnectorsProperties connectorsProperties;
    private final BusinessMenuConfigurationProvider menuConfigurationProvider;
    private final ServiceFunctionExecutor serviceFunctionExecutor;

    public WhatsappWebhookController(
            WhatsappService whatsappService,
            OAuthSessionService oauthSessionService,
            WhatsappSessionService sessionService,
            KeycloakAuthService keycloakAuthService,
            ProductService productService,
            InvoiceService invoiceService,
            TroubleTicketService troubleTicketService,
            @Value("${whatsapp.verify-token}") String verifyToken,
            OperationsMonitoringService monitoringService,
            ConnectorsProperties connectorsProperties,
            BusinessMenuConfigurationProvider menuConfigurationProvider,
            ServiceFunctionExecutor serviceFunctionExecutor) {
        this.whatsappService = whatsappService;
        this.oauthSessionService = oauthSessionService;
        this.sessionService = sessionService;
        this.keycloakAuthService = keycloakAuthService;
        this.productService = productService;
        this.invoiceService = invoiceService;
        this.troubleTicketService = troubleTicketService;
        this.verifyToken = Objects.requireNonNull(verifyToken, "whatsapp.verify-token must be set");
        this.monitoringService = monitoringService;
        this.connectorsProperties = connectorsProperties;
        this.menuConfigurationProvider = menuConfigurationProvider;
        this.serviceFunctionExecutor = serviceFunctionExecutor;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        if (!connectorsProperties.isWhatsappEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("WhatsApp connector is disabled");
        }

        log.info("WhatsApp webhook verification request mode={} token={}", mode, token);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge == null ? "" : challenge);
        }

        return ResponseEntity.status(403).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<Void> onEvent(@RequestBody Map<String, Object> payload) {
        if (!connectorsProperties.isWhatsappEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
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
                    if ("interactive".equals(type)) {
                        Map<String, Object> interactive = (Map<String, Object>) message.get("interactive");
                        String id = extractInteractiveId(interactive);
                        if (id == null) {
                            dispatchFallback(from);
                            continue;
                        }
                        handleCommand(from, id.trim());
                        continue;
                    }

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
        String optInYesText = whatsappService.translate(userId, WhatsappService.KEY_OPT_IN_YES).toLowerCase();
        String optInNoText = whatsappService.translate(userId, WhatsappService.KEY_OPT_IN_NO).toLowerCase();
        String menuText = whatsappService.translate(userId, WhatsappService.KEY_BUTTON_MENU).toLowerCase();
        boolean awaitingLanguage = sessionService.isAwaitingLanguageSelection(userId);
        var tokenSnapshot = sessionService.getTokenSnapshot(userId);
        String token = sessionService.getValidAccessToken(userId);
        boolean hasValidToken = token != null;
        boolean optedIn = sessionService.isOptedIn(userId);
        monitoringService.recordActivity("WhatsApp", userId, null, hasValidToken,
                monitoringService.toTokenDetails(tokenSnapshot), optedIn);
        WhatsappSessionService.SelectionContext selectionContext = sessionService.getSelectionContext(userId);
        List<LoginMenuItem> loginMenuOptions = whatsappService.loginMenuOptions(userId);
        LoginMenuItem selectedLoginItem = (!hasValidToken
                && selectionContext == WhatsappSessionService.SelectionContext.NONE)
                ? parseLoginMenuSelection(body, loginMenuOptions)
                : null;
        LoginMenuFunction selectedLoginFunction = selectedLoginItem == null ? null : selectedLoginItem.resolvedFunction();

        boolean isCrmLogin = selectedLoginFunction == LoginMenuFunction.CRM_LOGIN
                || lower.equals(WhatsappService.INTERACTIVE_ID_CRM_LOGIN.toLowerCase())
                || lower.contains(TelegramService.CALLBACK_DIRECT_LOGIN.toLowerCase());
        boolean isDigitalLogin = !isCrmLogin && (selectedLoginFunction == LoginMenuFunction.DIGITAL_LOGIN
                || lower.equals(WhatsappService.INTERACTIVE_ID_DIGITAL_LOGIN.toLowerCase())
                || lower.contains(TelegramService.CALLBACK_SELF_SERVICE_LOGIN.toLowerCase())
                || lower.equals("login"));
        boolean isMenuSelection = lower.equals(WhatsappService.INTERACTIVE_ID_MENU.toLowerCase())
                || lower.equals(menuText);
        boolean isOptInSelection = selectedLoginFunction == LoginMenuFunction.OPT_IN
                || lower.equals(WhatsappService.INTERACTIVE_ID_OPT_IN.toLowerCase())
                || lower.contains(TelegramService.CALLBACK_OPT_IN_PROMPT.toLowerCase())
                || lower.equals(optInYesText)
                || lower.equals(optInNoText)
                || isMenuSelection;
        boolean isChangeLanguage = selectedLoginFunction == LoginMenuFunction.CHANGE_LANGUAGE
                || lower.equals(WhatsappService.INTERACTIVE_ID_CHANGE_LANGUAGE.toLowerCase())
                || lower.equals(WhatsappService.COMMAND_CHANGE_LANGUAGE);
        boolean isSettingsSelection = selectedLoginFunction == LoginMenuFunction.SETTINGS
                || lower.equals(WhatsappService.INTERACTIVE_ID_SETTINGS.toLowerCase())
                || lower.equals("settings");

        if (isMenuSelection && selectionContext == WhatsappSessionService.SelectionContext.NONE) {
            if (hasValidToken && ensureAccountSelected(sessionKey, userId)) {
                sendBusinessMenu(from, userId);
            } else {
                sendLoginPrompt(from, sessionKey);
            }
            return;
        }

        if (selectionContext == WhatsappSessionService.SelectionContext.OPT_IN) {
            int choice = parseIndex(lower);
            if (choice == 1 || lower.equals(optInYesText)) {
                sessionService.setOptIn(userId, true);
                monitoringService.recordActivity("WhatsApp", userId, null, hasValidToken,
                        monitoringService.toTokenDetails(tokenSnapshot), true);
                whatsappService.sendOptInAccepted(from);
                sessionService.setSelectionContext(userId, WhatsappSessionService.SelectionContext.NONE);
                if (hasValidToken && ensureAccountSelected(sessionKey, userId)) {
                    sendBusinessMenu(from, userId);
                } else {
                    sendLoginPrompt(from, sessionKey);
                }
                return;
            }
            if (choice == 2 || lower.equals(optInNoText)) {
                sessionService.setOptIn(userId, false);
                monitoringService.recordActivity("WhatsApp", userId, null, hasValidToken,
                        monitoringService.toTokenDetails(tokenSnapshot), false);
                whatsappService.sendText(from, whatsappService.translate(from, "OptOutConfirmation"));
                sessionService.setSelectionContext(userId, WhatsappSessionService.SelectionContext.NONE);
                if (hasValidToken && ensureAccountSelected(sessionKey, userId)) {
                    sendBusinessMenu(from, userId);
                } else {
                    sendLoginPrompt(from, sessionKey);
                }
                return;
            }
            if (choice == 3 || lower.equals(menuText)) {
                sessionService.setSelectionContext(userId, WhatsappSessionService.SelectionContext.NONE);
                if (hasValidToken && ensureAccountSelected(sessionKey, userId)) {
                    sendBusinessMenu(from, userId);
                } else {
                    sendLoginPrompt(from, sessionKey);
                }
                return;
            }
        }

        if (selectionContext == WhatsappSessionService.SelectionContext.SETTINGS) {
            List<LoginMenuItem> settingsOptions = whatsappService.loginSettingsMenuOptions(userId);
            LoginMenuItem settingsSelection = parseLoginMenuSelection(body, settingsOptions);
            if (settingsSelection == null && lower.equals(menuText)) {
                settingsSelection = settingsOptions.stream()
                        .filter(item -> item.resolvedFunction() == LoginMenuFunction.MENU)
                        .findFirst()
                        .orElse(null);
            }
            if (settingsSelection != null) {
                LoginMenuFunction selectionFunction = settingsSelection.resolvedFunction();
                sessionService.setSelectionContext(userId, WhatsappSessionService.SelectionContext.NONE);
                if (selectionFunction == LoginMenuFunction.OPT_IN) {
                    whatsappService.sendOptInPrompt(from);
                    return;
                }
                if (selectionFunction == LoginMenuFunction.CHANGE_LANGUAGE) {
                    sessionService.setAwaitingLanguageSelection(userId, true);
                    whatsappService.sendLanguageMenu(from);
                    return;
                }
                if (selectionFunction == LoginMenuFunction.MENU) {
                    if (hasValidToken && ensureAccountSelected(sessionKey, userId)) {
                        sendBusinessMenu(from, userId);
                    } else {
                        sendLoginPrompt(from, sessionKey);
                    }
                    return;
                }
                if (selectionFunction == LoginMenuFunction.SETTINGS) {
                    whatsappService.sendSettingsMenu(from);
                    return;
                }
            }
        }

        if (lower.equals("unsubscribe")) {
            sessionService.setOptIn(userId, false);
            monitoringService.recordActivity("WhatsApp", userId, null, hasValidToken,
                    monitoringService.toTokenDetails(tokenSnapshot), false);
            whatsappService.sendText(from, whatsappService.translate(from, "OptOutConfirmation"));
            return;
        }

        if (lower.startsWith("yes") && lower.contains("opt-in")) {
            sessionService.setOptIn(userId, true);
            monitoringService.recordActivity("WhatsApp", userId, null, hasValidToken,
                    monitoringService.toTokenDetails(tokenSnapshot), true);
            whatsappService.sendOptInAccepted(from);
            sessionService.setSelectionContext(userId, WhatsappSessionService.SelectionContext.NONE);
            sendLoginPrompt(from, sessionKey);
            return;
        }

        if (lower.startsWith("no") && lower.contains("opt-in")) {
            sessionService.setOptIn(userId, false);
            monitoringService.recordActivity("WhatsApp", userId, null, hasValidToken,
                    monitoringService.toTokenDetails(tokenSnapshot), false);
            whatsappService.sendText(from, whatsappService.translate(from, "OptOutConfirmation"));
            sessionService.setSelectionContext(userId, WhatsappSessionService.SelectionContext.NONE);
            sendLoginPrompt(from, sessionKey);
            return;
        }

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

        if (isOptInSelection) {
            whatsappService.sendOptInPrompt(from);
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

        if (isDigitalLogin) {
            if (!hasValidToken) {
                whatsappService.sendDigitalLoginLink(from, oauthSessionService.buildAuthUrl("WhatsApp", sessionKey));
                return;
            }
            // Logged-in users should treat numeric options as business menu selections, not login shortcuts.
        }

        if (isCrmLogin) {
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
                    apiResponse = troubleTicketService.callTroubleTicket(accessToken);
                }
                whatsappService.sendText(from, authMessage + "\n\n" +
                        whatsappService.format(from, "ExternalApiResult", apiResponse));
                sendLoginPrompt(from, sessionKey);
                return;
            }
        }

        if (!hasValidToken) {
            if (isChangeLanguage) {
                sessionService.setAwaitingLanguageSelection(userId, true);
                whatsappService.sendLanguageMenu(from);
                return;
            }
            if (isSettingsSelection) {
                whatsappService.sendSettingsMenu(from);
                return;
            }
            sendLoginPrompt(from, sessionKey);
            return;
        }

        if (isSettingsSelection) {
            whatsappService.sendSettingsMenu(from);
            return;
        }

        if (lower.startsWith("account")) {
            handleAccountSelection(sessionKey, userId, lower);
            return;
        }

        if (selectionContext == WhatsappSessionService.SelectionContext.ACCOUNT && parseIndex(lower) >= 1) {
            int selection = parseIndex(lower);
            List<AccountSummary> accounts = sessionService.getAccounts(userId);
            int start = sessionService.getSelectionPageStart(userId);
            int end = Math.min(accounts.size(), start + 5);
            int moreIndex = end + 1;
            if (selection == moreIndex && end < accounts.size()) {
                whatsappService.sendAccountPage(from, accounts, end);
                return;
            }
            int accountIndex = selection - 1;
            if (accountIndex >= start && accountIndex < end) {
                handleAccountSelection(sessionKey, userId, "account " + lower);
                return;
            }
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

        if (selectionContext == WhatsappSessionService.SelectionContext.SERVICE && parseIndex(lower) >= 1) {
            int selection = parseIndex(lower);
            List<ServiceSummary> services = sessionService.getServices(userId);
            int start = sessionService.getSelectionPageStart(userId);
            int end = Math.min(services.size(), start + 5);
            int moreIndex = end + 1;
            if (selection == moreIndex && end < services.size()) {
                whatsappService.sendServicePage(from, services, end);
                return;
            }
            int serviceIndex = selection - 1;
            if (serviceIndex >= start && serviceIndex < end) {
                handleServiceSelection(userId, from, "service " + lower);
                return;
            }
        }

        if (lower.startsWith("more services")) {
            int offset = parseIndex(lower.replace("more services", "").trim());
            whatsappService.sendServicePage(from, sessionService.getServices(userId), offset);
            return;
        }

        if (lower.startsWith("invoice")) {
            handleInvoiceSelection(userId, from, lower);
            return;
        }

        if (selectionContext == WhatsappSessionService.SelectionContext.INVOICE && parseIndex(lower) >= 1) {
            int selection = parseIndex(lower);
            List<InvoiceSummary> invoices = sessionService.getInvoices(userId);
            int start = sessionService.getSelectionPageStart(userId);
            int end = Math.min(invoices.size(), start + 5);
            int moreIndex = end + 1;
            if (selection == moreIndex && end < invoices.size()) {
                whatsappService.sendInvoicePage(from, invoices, end);
                return;
            }
            int invoiceIndex = selection - 1;
            if (invoiceIndex >= start && invoiceIndex < end) {
                handleInvoiceSelection(userId, from, "invoice " + lower);
                return;
            }
        }

        if (lower.startsWith("more invoices")) {
            int offset = parseIndex(lower.replace("more invoices", "").trim());
            whatsappService.sendInvoicePage(from, sessionService.getInvoices(userId), offset);
            return;
        }

        if (selectionContext == WhatsappSessionService.SelectionContext.INVOICE_ACTION && parseIndex(lower) >= 1) {
            handleInvoiceActionSelection(userId, from, parseIndex(lower));
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

        if (selectionContext == WhatsappSessionService.SelectionContext.TICKET && parseIndex(lower) >= 1) {
            int selection = parseIndex(lower);
            List<TroubleTicketSummary> tickets = sessionService.getTroubleTickets(userId);
            int start = sessionService.getSelectionPageStart(userId);
            int end = Math.min(tickets.size(), start + 5);
            int moreIndex = end + 1;
            if (selection == moreIndex && end < tickets.size()) {
                whatsappService.sendTroubleTicketPage(from, tickets, end);
                return;
            }
            int ticketIndex = selection - 1;
            if (ticketIndex >= start && ticketIndex < end) {
                handleTicketSelection(userId, from, selection);
                return;
            }
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
            monitoringService.markLoggedOut("WhatsApp", userId);
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
            if (item.isWeblink()) {
                whatsappService.sendWeblink(from, item);
                AccountSummary selected = sessionService.getSelectedAccount(userId);
                whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
                return;
            }

            if (!item.isFunctionMenu() && item.isSubMenu()) {
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
                case TelegramService.CALLBACK_INVOICE_HISTORY -> handleInvoiceHistory(sessionKey, userId, token);
                case TelegramService.CALLBACK_MENU -> {
                    whatsappService.goHomeBusinessMenu(userId);
                    AccountSummary selected = sessionService.getSelectedAccount(userId);
                    whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
                }
                default -> {
                    if (!ensureAccountSelected(sessionKey, userId)) {
                        return;
                    }
                    AccountSummary selected = sessionService.getSelectedAccount(userId);
                    ServiceSummary selectedService = sessionService.getSelectedService(userId);
                    ServiceFunctionExecutor.ExecutionResult result = serviceFunctionExecutor
                            .execute(item.function(), token, selected, selectedService);
                    if (result.handled()) {
                        if (result.mode() == ServiceFunctionExecutor.ResponseMode.CARD) {
                            whatsappService.sendCardMessage(from, result.message(), result.buttons());
                        } else {
                            whatsappService.sendText(from, result.message());
                        }
                        whatsappService.sendLoggedInMenu(from, selected,
                                sessionService.getAccounts(userId).size() > 1);
                    } else {
                        sendBusinessMenu(from, userId);
                    }
                }
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
            whatsappService.sendSettingsMenu(from);
            return;
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
            monitoringService.markLoggedOut("WhatsApp", userId);
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
            sessionService.selectService(userId, selectedService);
            String name = (selectedService.productName() == null || selectedService.productName().isBlank())
                    ? whatsappService.translate(userId, "UnknownService")
                    : selectedService.productName().strip();
            String number = (selectedService.accessNumber() == null || selectedService.accessNumber().isBlank())
                    ? whatsappService.translate(userId, "NoAccessNumber")
                    : selectedService.accessNumber().strip();
            whatsappService.sendText(from, whatsappService.format(userId, "ServiceSelected", name, number));
        }
        sessionService.setSelectionContext(userId, WhatsappSessionService.SelectionContext.NONE);
        sendBusinessMenu(from, userId);
    }

    private void handleServiceLookup(String sessionKey, String userId, String token) {
        if (!ensureAccountSelected(sessionKey, userId)) {
            return;
        }
        AccountSummary selected = sessionService.getSelectedAccount(userId);
        ServiceListResult services = productService.getMainServices(token, selected.accountId());
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
            if (services.services().size() == 1) {
                ServiceSummary onlyService = services.services().get(0);
                sessionService.selectService(userId, onlyService);
                String name = (onlyService.productName() == null || onlyService.productName().isBlank())
                        ? whatsappService.translate(userId, "UnknownService")
                        : onlyService.productName().strip();
                String number = (onlyService.accessNumber() == null || onlyService.accessNumber().isBlank())
                        ? whatsappService.translate(userId, "NoAccessNumber")
                        : onlyService.accessNumber().strip();
                whatsappService.sendText(userId, whatsappService.format(userId, "ServiceSelected", name, number));
                whatsappService.sendLoggedInMenu(userId, selected, sessionService.getAccounts(userId).size() > 1);
            } else {
                whatsappService.sendServicePage(userId, services.services(), 0);
            }
        }
    }

    private void handleInvoiceHistory(String sessionKey, String userId, String token) {
        if (!ensureAccountSelected(sessionKey, userId)) {
            return;
        }
        BusinessMenuItem invoiceMenuItem = menuConfigurationProvider
                .findMenuItemByCallback(TelegramService.CALLBACK_INVOICE_HISTORY);
        if (invoiceMenuItem != null && invoiceMenuItem.isFunctionMenu()) {
            sessionService.setInvoiceActionsMenu(userId, invoiceMenuItem.submenuId());
        } else {
            sessionService.setInvoiceActionsMenu(userId, null);
        }
        AccountSummary selected = sessionService.getSelectedAccount(userId);
        InvoiceListResult invoices = invoiceService.getInvoices(token, selected.accountId());
        if (invoices.hasError()) {
            sessionService.clearInvoices(userId);
            whatsappService.sendText(userId,
                    whatsappService.format(userId, "UnableToRetrieveInvoices", invoices.errorMessage()));
            whatsappService.sendLoggedInMenu(userId, selected, sessionService.getAccounts(userId).size() > 1);
            return;
        }
        if (invoices.invoices().isEmpty()) {
            sessionService.clearInvoices(userId);
            whatsappService.sendText(userId,
                    whatsappService.format(userId, "NoInvoicesForAccount", selected.accountId()));
            whatsappService.sendLoggedInMenu(userId, selected, sessionService.getAccounts(userId).size() > 1);
            return;
        }

        sessionService.saveInvoices(userId, invoices.invoices());
        whatsappService.sendInvoicePage(userId, invoices.invoices(), 0);
    }

    private void handleInvoiceSelection(String userId, String from, String lower) {
        List<InvoiceSummary> invoices = sessionService.getInvoices(userId);
        int index = parseIndex(lower.replace("invoice", "").trim()) - 1;
        if (invoices.isEmpty() || index < 0 || index >= invoices.size()) {
            whatsappService.sendText(from, whatsappService.translate(userId, "InvoiceNoLongerAvailable"));
            whatsappService.sendInvoicePage(from, invoices, sessionService.getSelectionPageStart(userId));
            return;
        }
        InvoiceSummary invoice = invoices.get(index);
        sessionService.selectInvoice(userId, invoice);
        whatsappService.sendText(from, whatsappService.format(userId, "InvoiceSelected", invoice.id()));
        whatsappService.sendInvoiceActions(from, invoice);
    }

    private void handleInvoiceActionSelection(String userId, String from, int numeric) {
        InvoiceSummary selectedInvoice = sessionService.getSelectedInvoice(userId);
        if (selectedInvoice == null) {
            whatsappService.sendText(from, whatsappService.translate(userId, "InvoiceNoLongerAvailable"));
            AccountSummary selected = sessionService.getSelectedAccount(userId);
            whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
            return;
        }
        List<BusinessMenuItem> actions = whatsappService.invoiceActions(userId);
        if (numeric < 1 || numeric > actions.size()) {
            whatsappService.sendText(from, whatsappService.translate(userId, "InvoiceActionsInstruction"));
            return;
        }
        BusinessMenuItem action = actions.get(numeric - 1);
        String callback = whatsappService.invoiceActionCallback(userId, action, selectedInvoice);
        if (TelegramService.CALLBACK_MENU.equalsIgnoreCase(callback)) {
            whatsappService.goHomeBusinessMenu(userId);
            AccountSummary selected = sessionService.getSelectedAccount(userId);
            whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
            return;
        }
        String translationKey = invoiceActionTranslationKey(callback);
        if (translationKey == null) {
            whatsappService.sendText(from, whatsappService.translate(userId, "InvoiceActionsInstruction"));
            return;
        }
        whatsappService.sendText(from, whatsappService.format(userId, translationKey, selectedInvoice.id()));
        whatsappService.sendInvoiceActions(from, selectedInvoice);
    }

    private void handleTroubleTickets(String sessionKey, String userId, String token) {
        if (!ensureAccountSelected(sessionKey, userId)) {
            return;
        }
        AccountSummary selected = sessionService.getSelectedAccount(userId);
        ServiceSummary selectedService = sessionService.getSelectedService(userId);
        TroubleTicketListResult result = troubleTicketService.getTroubleTicketsByAccountId(token, selected.accountId(),
                selectedService == null ? null : selectedService.productId());
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

    private void handleTicketSelection(String userId, String from, int numeric) {
        List<TroubleTicketSummary> tickets = sessionService.getTroubleTickets(userId);
        int index = numeric - 1;
        if (tickets.isEmpty() || index < 0 || index >= tickets.size()) {
            whatsappService.sendText(from, whatsappService.translate(userId, "TicketNoLongerAvailable"));
        } else {
            TroubleTicketSummary ticket = tickets.get(index);
            whatsappService.sendText(from, whatsappService.format(userId, "TicketSelected", ticket.id()));
        }
        sessionService.setSelectionContext(userId, WhatsappSessionService.SelectionContext.NONE);
        AccountSummary selected = sessionService.getSelectedAccount(userId);
        whatsappService.sendLoggedInMenu(from, selected, sessionService.getAccounts(userId).size() > 1);
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
        var tokenSnapshot = sessionService.getTokenSnapshot(from);
        boolean hasValidToken = sessionService.getValidAccessToken(from) != null;
        monitoringService.recordActivity("WhatsApp", from, null, hasValidToken,
                monitoringService.toTokenDetails(tokenSnapshot), sessionService.isOptedIn(from));
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
        whatsappService.sendLoginMenu(to);
    }

    private int parseIndex(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception ex) {
            return -1;
        }
    }

    private String invoiceActionTranslationKey(String callback) {
        if (callback == null || callback.isBlank()) {
            return null;
        }
        if (callback.startsWith(TelegramService.CALLBACK_INVOICE_VIEW_PDF_PREFIX)) {
            return "InvoiceViewPdf";
        }
        if (callback.startsWith(TelegramService.CALLBACK_INVOICE_PAY_PREFIX)) {
            return "InvoicePay";
        }
        if (callback.startsWith(TelegramService.CALLBACK_INVOICE_COMPARE_PREFIX)) {
            return "InvoiceCompare";
        }
        return null;
    }

    private LoginMenuItem parseLoginMenuSelection(String text, List<LoginMenuItem> options) {
        int numeric = parseIndex(text);
        if (numeric > 0 && numeric <= options.size()) {
            return options.get(numeric - 1);
        }
        for (LoginMenuItem option : options) {
            if (text.equalsIgnoreCase(whatsappService.callbackId(option))) {
                return option;
            }
        }
        return null;
    }

    private String extractInteractiveId(Map<String, Object> interactive) {
        if (interactive == null) {
            return null;
        }
        Map<String, Object> button = (Map<String, Object>) interactive.get("button");
        if (button != null) {
            Map<String, Object> reply = (Map<String, Object>) button.get("reply");
            return reply == null ? null : (String) reply.get("id");
        }
        Map<String, Object> listReply = (Map<String, Object>) interactive.get("list_reply");
        if (listReply != null) {
            return (String) listReply.get("id");
        }
        return null;
    }
}
