package com.selfservice.telegrambot.controller;

import com.selfservice.application.auth.KeycloakAuthService;
import com.selfservice.application.auth.OAuthSessionService;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.InvoiceListResult;
import com.selfservice.application.dto.InvoiceSummary;
import com.selfservice.application.dto.ServiceListResult;
import com.selfservice.application.dto.ServiceSummary;
import com.selfservice.application.dto.TroubleTicketListResult;
import com.selfservice.application.service.ProductService;
import com.selfservice.application.service.InvoiceService;
import com.selfservice.application.service.TroubleTicketService;
import com.selfservice.application.service.OperationsMonitoringService;
import com.selfservice.application.service.ServiceFunctionExecutor;
import com.selfservice.application.config.menu.LoginMenuFunction;
import com.selfservice.application.config.menu.LoginMenuItem;
import com.selfservice.application.config.menu.BusinessMenuConfigurationProvider;
import com.selfservice.application.config.menu.BusinessMenuItem;
import com.selfservice.application.config.ConnectorsProperties;
import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.telegrambot.service.UserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhook/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private final TelegramService telegramService;
    private final KeycloakAuthService keycloakAuthService;
    private final ProductService productService;
    private final OAuthSessionService oauthSessionService;
    private final UserSessionService userSessionService;
    private final InvoiceService invoiceService;
    private final TroubleTicketService troubleTicketService;
    private final OperationsMonitoringService monitoringService;
    private final ConnectorsProperties connectorsProperties;
    private final BusinessMenuConfigurationProvider menuConfigurationProvider;
    private final ServiceFunctionExecutor serviceFunctionExecutor;

    public TelegramWebhookController(TelegramService telegramService,
            KeycloakAuthService keycloakAuthService,
            ProductService productService,
            OAuthSessionService oauthSessionService,
            UserSessionService userSessionService,
            InvoiceService invoiceService,
            TroubleTicketService troubleTicketService,
            OperationsMonitoringService monitoringService,
            ConnectorsProperties connectorsProperties,
            BusinessMenuConfigurationProvider menuConfigurationProvider,
            ServiceFunctionExecutor serviceFunctionExecutor) {
        this.telegramService = telegramService;
        this.keycloakAuthService = keycloakAuthService;
        this.productService = productService;
        this.oauthSessionService = oauthSessionService;

        this.userSessionService = userSessionService;
        this.invoiceService = invoiceService;
        this.troubleTicketService = troubleTicketService;
        this.monitoringService = monitoringService;
        this.connectorsProperties = connectorsProperties;
        this.menuConfigurationProvider = menuConfigurationProvider;
        this.serviceFunctionExecutor = serviceFunctionExecutor;

    }

    @PostMapping
    public ResponseEntity<Void> onUpdate(@RequestBody Map<String, Object> update) {
        if (!connectorsProperties.isTelegramEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        log.info("Incoming Telegram update: {}", update);

        try {

            Map<String, Object> message = (Map<String, Object>) update.get("message");
            Map<String, Object> callbackQuery = (Map<String, Object>) update.get("callback_query");
            Map<String, Object> chat;
            String text;
            long chatId;
            String chatUsername;

            if (message != null) {
                chat = (Map<String, Object>) message.get("chat");
                if (chat == null || chat.get("id") == null)
                    return ResponseEntity.ok().build();

                chatId = ((Number) chat.get("id")).longValue();
                chatUsername = extractDisplayName(chat);
                text = (String) message.get("text");
                if (text == null)
                    text = "";
                text = text.trim();
            } else if (callbackQuery != null) {
                Map<String, Object> callbackMessage = (Map<String, Object>) callbackQuery.get("message");
                if (callbackMessage == null)
                    return ResponseEntity.ok().build();

                chat = (Map<String, Object>) callbackMessage.get("chat");
                if (chat == null || chat.get("id") == null)
                    return ResponseEntity.ok().build();

                chatId = ((Number) chat.get("id")).longValue();
                chatUsername = extractDisplayName(chat);
                text = (String) callbackQuery.get("data");
                if (text == null)
                    text = "";
                text = text.trim();

                telegramService.answerCallbackQuery((String) callbackQuery.get("id"));
            } else {


                return ResponseEntity.ok().build();

            }

            if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_HELLO_WORLD))) {
                text = TelegramService.CALLBACK_HELLO_WORLD;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_HELLO_CERILLION))) {
                text = TelegramService.CALLBACK_HELLO_CERILLION;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_TROUBLE_TICKET))) {
                text = TelegramService.CALLBACK_TROUBLE_TICKET;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_SELECT_SERVICE))) {
                text = TelegramService.CALLBACK_SELECT_SERVICE;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_MY_ISSUES))) {
                text = TelegramService.CALLBACK_MY_ISSUES;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_INVOICE_HISTORY))) {
                text = TelegramService.CALLBACK_INVOICE_HISTORY;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_SELF_SERVICE_LOGIN))) {
                text = TelegramService.CALLBACK_SELF_SERVICE_LOGIN;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_DIRECT_LOGIN))) {
                text = TelegramService.CALLBACK_DIRECT_LOGIN;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_OPT_IN))) {
                text = TelegramService.CALLBACK_OPT_IN_PROMPT;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_SETTINGS))) {
                text = TelegramService.CALLBACK_SETTINGS_MENU;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_MENU))) {
                text = TelegramService.CALLBACK_MENU;
            } else if (text.equalsIgnoreCase(telegramService.translate(chatId, TelegramService.KEY_OPT_IN_YES))) {
                text = TelegramService.CALLBACK_OPT_IN_ACCEPT;
            } else if (text.equalsIgnoreCase(telegramService.translate(chatId, TelegramService.KEY_OPT_IN_NO))) {
                text = TelegramService.CALLBACK_OPT_IN_DECLINE;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_LOGOUT))) {
                text = TelegramService.CALLBACK_LOGOUT;
            }

            var tokenSnapshot = userSessionService.getTokenSnapshot(chatId);
            String existingToken = userSessionService.getValidAccessToken(chatId);
            boolean hasValidToken = existingToken != null;
            boolean optedIn = userSessionService.isOptedIn(chatId);
            monitoringService.recordActivity("Telegram", Long.toString(chatId), chatUsername, hasValidToken,
                    monitoringService.toTokenDetails(tokenSnapshot), optedIn);
            String loginReminder = telegramService.format(chatId, "LoginReminder",
                    telegramService.translate(chatId, TelegramService.KEY_BUTTON_SELF_SERVICE_LOGIN));

            LoginMenuItem loginMenuItem = telegramService.findLoginMenuItemByCallback(text);
            if (loginMenuItem != null) {
                LoginMenuFunction function = loginMenuItem.resolvedFunction();
                if (function == LoginMenuFunction.DIGITAL_LOGIN) {
                    text = TelegramService.CALLBACK_SELF_SERVICE_LOGIN;
                } else if (function == LoginMenuFunction.CRM_LOGIN) {
                    text = TelegramService.CALLBACK_DIRECT_LOGIN;
                } else if (function == LoginMenuFunction.OPT_IN) {
                    text = TelegramService.CALLBACK_OPT_IN_PROMPT;
                } else if (function == LoginMenuFunction.CHANGE_LANGUAGE) {
                    text = TelegramService.CALLBACK_LANGUAGE_MENU;
                } else if (function == LoginMenuFunction.SETTINGS) {
                    text = TelegramService.CALLBACK_SETTINGS_MENU;
                } else if (function == LoginMenuFunction.MENU) {
                    text = TelegramService.CALLBACK_MENU;
                }
            }

            if (text.equalsIgnoreCase("unsubscribe")) {
                userSessionService.setOptIn(chatId, false);
                monitoringService.recordActivity("Telegram", Long.toString(chatId), chatUsername, hasValidToken,
                        monitoringService.toTokenDetails(tokenSnapshot), false);
                telegramService.sendMessageWithKey(chatId, "OptOutConfirmation");
                return ResponseEntity.ok().build();
            }

            if (text.equals(TelegramService.CALLBACK_BUSINESS_MENU_HOME)) {
                telegramService.goHomeBusinessMenu(chatId);
                if (hasValidToken && ensureAccountSelected(chatId)) {
                    AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                    telegramService.sendLoggedInMenu(chatId, selected,
                            userSessionService.getAccounts(chatId).size() > 1);
                } else {
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (text.equals(TelegramService.CALLBACK_SETTINGS_MENU)) {
                telegramService.sendSettingsMenu(chatId);
                return ResponseEntity.ok().build();
            }

            if (text.equals(TelegramService.CALLBACK_MENU)) {
                telegramService.goHomeBusinessMenu(chatId);
                telegramService.goHomeLoginMenu(chatId);
                if (hasValidToken && ensureAccountSelected(chatId)) {
                    AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                    telegramService.sendLoggedInMenu(chatId, selected,
                            userSessionService.getAccounts(chatId).size() > 1);
                } else {
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (text.equals(TelegramService.CALLBACK_BUSINESS_MENU_UP)) {
                if (hasValidToken && ensureAccountSelected(chatId)) {
                    telegramService.goUpBusinessMenu(chatId);
                    AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                    telegramService.sendLoggedInMenu(chatId, selected,
                            userSessionService.getAccounts(chatId).size() > 1);
                } else {
                    telegramService.goUpLoginMenu(chatId);
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_BUSINESS_MENU_PREFIX)) {
                String menuId = text.substring(TelegramService.CALLBACK_BUSINESS_MENU_PREFIX.length()).trim();
                if (!telegramService.goToBusinessMenu(chatId, menuId)) {
                    telegramService.sendMessageWithKey(chatId, "BusinessMenuUnavailable");
                }
                if (hasValidToken && ensureAccountSelected(chatId)) {
                    AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                    telegramService.sendLoggedInMenu(chatId, selected,
                            userSessionService.getAccounts(chatId).size() > 1);
                } else {
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_SHOW_MORE_ACCOUNTS_PREFIX)) {
                int offset = parseIndex(text, TelegramService.CALLBACK_SHOW_MORE_ACCOUNTS_PREFIX);
                var accounts = userSessionService.getAccounts(chatId);
                if (accounts.isEmpty()) {
                    telegramService.sendMessageWithKey(chatId, "NoStoredAccounts");
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                } else {
                    telegramService.sendAccountPage(chatId, accounts, offset);
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_SHOW_MORE_SERVICES_PREFIX)) {
                int offset = parseIndex(text, TelegramService.CALLBACK_SHOW_MORE_SERVICES_PREFIX);
                List<ServiceSummary> services = userSessionService.getServices(chatId);
                if (services.isEmpty()) {
                    userSessionService.clearServices(chatId);
                    telegramService.sendMessageWithKey(chatId, "ServiceNoLongerAvailable");
                } else {
                    telegramService.sendServicePage(chatId, services, offset);
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_SHOW_MORE_TICKETS_PREFIX)) {
                int offset = parseIndex(text, TelegramService.CALLBACK_SHOW_MORE_TICKETS_PREFIX);
                var tickets = userSessionService.getTroubleTickets(chatId);
                if (tickets.isEmpty()) {
                    userSessionService.clearTroubleTickets(chatId);
                    telegramService.sendMessageWithKey(chatId, "TicketNoLongerAvailable");
                } else {
                    telegramService.sendTroubleTicketPage(chatId, tickets, offset);
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_SHOW_MORE_INVOICES_PREFIX)) {
                int offset = parseIndex(text, TelegramService.CALLBACK_SHOW_MORE_INVOICES_PREFIX);
                var invoices = userSessionService.getInvoices(chatId);
                if (invoices.isEmpty()) {
                    userSessionService.clearInvoices(chatId);
                    telegramService.sendMessageWithKey(chatId, "InvoiceNoLongerAvailable");
                } else {
                    telegramService.sendInvoicePage(chatId, invoices, offset);
                }
                return ResponseEntity.ok().build();
            }

            if (TelegramService.CALLBACK_LANGUAGE_MENU.equals(text)) {
                telegramService.sendLanguageMenu(chatId);
                return ResponseEntity.ok().build();
            }

            if (TelegramService.CALLBACK_OPT_IN_PROMPT.equals(text)) {
                telegramService.sendOptInPrompt(chatId);
                return ResponseEntity.ok().build();
            }

            if (TelegramService.CALLBACK_OPT_IN_ACCEPT.equals(text)) {
                userSessionService.setOptIn(chatId, true);
                monitoringService.recordActivity("Telegram", Long.toString(chatId), chatUsername, hasValidToken,
                        monitoringService.toTokenDetails(tokenSnapshot), true);
                telegramService.sendMessageWithKey(chatId, "OptInAccepted");
                if (hasValidToken && ensureAccountSelected(chatId)) {
                    AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                    telegramService.sendLoggedInMenu(chatId, selected,
                            userSessionService.getAccounts(chatId).size() > 1);
                } else {
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (TelegramService.CALLBACK_OPT_IN_DECLINE.equals(text)) {
                userSessionService.setOptIn(chatId, false);
                monitoringService.recordActivity("Telegram", Long.toString(chatId), chatUsername, hasValidToken,
                        monitoringService.toTokenDetails(tokenSnapshot), false);
                telegramService.sendMessageWithKey(chatId, "OptInDeclined");
                if (hasValidToken && ensureAccountSelected(chatId)) {
                    AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                    telegramService.sendLoggedInMenu(chatId, selected,
                            userSessionService.getAccounts(chatId).size() > 1);
                } else {
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_LANGUAGE_PREFIX)) {
                String languageCode = text.substring(TelegramService.CALLBACK_LANGUAGE_PREFIX.length()).toLowerCase();
                if (!telegramService.isSupportedLanguage(languageCode)) {
                    telegramService.sendMessageWithKey(chatId, "LanguageNotSupported");
                } else {
                    userSessionService.setLanguage(chatId, languageCode);
                    String languageLabelKey = switch (languageCode) {
                        case "fr" -> "LanguageFrench";
                        case "pt" -> "LanguagePortuguese";
                        case "ru" -> "LanguageRussian";
                        case "en" -> "LanguageEnglish";
                        default -> "LanguageEnglish";
                    };
                    telegramService.sendMessageWithKey(chatId, "LanguageUpdated",
                            telegramService.translate(chatId, languageLabelKey));
                    if (hasValidToken && ensureAccountSelected(chatId)) {
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                    }
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_TROUBLE_TICKET_PREFIX)) {
                String ticketId = text.substring(TelegramService.CALLBACK_TROUBLE_TICKET_PREFIX.length()).trim();
                if (ticketId.isEmpty()) {
                    telegramService.sendMessageWithKey(chatId, "TicketNoLongerAvailable");
                } else {
                    telegramService.sendMessageWithKey(chatId, "TicketSelected", ticketId);
                }

                if (hasValidToken && ensureAccountSelected(chatId)) {
                    AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                    telegramService.sendLoggedInMenu(chatId, selected,
                            userSessionService.getAccounts(chatId).size() > 1);
                } else {
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_INVOICE_VIEW_PDF_PREFIX)) {
                String invoiceId = text.substring(TelegramService.CALLBACK_INVOICE_VIEW_PDF_PREFIX.length()).trim();
                handleInvoiceAction(chatId, invoiceId, "InvoiceViewPdf");
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_INVOICE_PAY_PREFIX)) {
                String invoiceId = text.substring(TelegramService.CALLBACK_INVOICE_PAY_PREFIX.length()).trim();
                handleInvoiceAction(chatId, invoiceId, "InvoicePay");
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_INVOICE_COMPARE_PREFIX)) {
                String invoiceId = text.substring(TelegramService.CALLBACK_INVOICE_COMPARE_PREFIX.length()).trim();
                handleInvoiceAction(chatId, invoiceId, "InvoiceCompare");
                return ResponseEntity.ok().build();
            }

            if (TelegramService.CALLBACK_LOGOUT.equals(text)) {
                String refreshToken = userSessionService.getRefreshToken(chatId);
                String idToken = userSessionService.getIdToken(chatId);
                try {
                    oauthSessionService.logout(refreshToken, idToken);
                } catch (Exception e) {
                    log.warn("Failed to revoke Keycloak session for chat {}", chatId, e);
                }
                userSessionService.clearSession(chatId);
                monitoringService.markLoggedOut("Telegram", Long.toString(chatId));
                telegramService.sendMessageWithKey(chatId, "LoggedOutMessage");
                telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_SERVICE_PREFIX)) {
                int index = parseIndex(text, TelegramService.CALLBACK_SERVICE_PREFIX);
                List<ServiceSummary> services = userSessionService.getServices(chatId);
                if (services.isEmpty() || index < 0 || index >= services.size()) {
                    telegramService.sendMessageWithKey(chatId, "ServiceNoLongerAvailable");
                } else {
                    ServiceSummary selectedService = services.get(index);
                    userSessionService.selectService(chatId, selectedService);
                    String name = (selectedService.productName() == null || selectedService.productName().isBlank())
                            ? telegramService.translate(chatId, "UnknownService")
                            : selectedService.productName().strip();
                    String number = (selectedService.accessNumber() == null || selectedService.accessNumber().isBlank())
                            ? telegramService.translate(chatId, "NoAccessNumber")
                            : selectedService.accessNumber().strip();
                    telegramService.sendMessageWithKey(chatId, "ServiceSelected", name, number);
                }

                if (hasValidToken && ensureAccountSelected(chatId)) {
                    AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                    telegramService.sendLoggedInMenu(chatId, selected,
                            userSessionService.getAccounts(chatId).size() > 1);
                } else {
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_INVOICE_PREFIX)) {
                int index = parseIndex(text, TelegramService.CALLBACK_INVOICE_PREFIX);
                List<InvoiceSummary> invoices = userSessionService.getInvoices(chatId);
                if (invoices.isEmpty() || index < 0 || index >= invoices.size()) {
                    userSessionService.clearInvoices(chatId);
                    telegramService.sendMessageWithKey(chatId, "InvoiceNoLongerAvailable");
                } else {
                    InvoiceSummary selectedInvoice = invoices.get(index);
                    userSessionService.selectInvoice(chatId, selectedInvoice);
                    telegramService.sendMessageWithKey(chatId, "InvoiceSelected", selectedInvoice.id());
                    telegramService.sendInvoiceActions(chatId, selectedInvoice);
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_ACCOUNT_PREFIX)) {
                int index = parseIndex(text, TelegramService.CALLBACK_ACCOUNT_PREFIX);
                var accounts = userSessionService.getAccounts(chatId);
                if (accounts.isEmpty() || index < 0 || index >= accounts.size()) {
                    telegramService.sendMessageWithKey(chatId, "AccountSelectionExpired");
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                } else {
                    var selected = accounts.get(index);
                    userSessionService.selectAccount(chatId, selected);
                    telegramService.sendMessageWithKey(chatId, "SelectedAccount", selected.displayLabel());
                    telegramService.sendLoggedInMenu(chatId, selected, accounts.size() > 1);
                }
                return ResponseEntity.ok().build();
            }

            if (TelegramService.CALLBACK_CHANGE_ACCOUNT.equals(text)) {
                var accounts = userSessionService.getAccounts(chatId);
                if (accounts.isEmpty()) {
                    userSessionService.clearSelectedAccount(chatId);
                    telegramService.sendMessageWithKey(chatId, "NoStoredAccounts");
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                } else {
                    userSessionService.clearSelectedAccount(chatId);
                    telegramService.sendMessageWithKey(chatId, "ChooseAccountToContinue");
                    telegramService.sendAccountPage(chatId, accounts, 0);
                }
                return ResponseEntity.ok().build();
            }

            switch (text) {
                case TelegramService.CALLBACK_HELLO_WORLD:
                case "1":
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        telegramService.sendMessageWithKey(chatId, "HelloWorldMessage");
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_HELLO_CERILLION:
                case "2":
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        telegramService.sendMessageWithKey(chatId, "HelloCerillionMessage");
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_TROUBLE_TICKET:
                case "5":
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        String ticketInfo = troubleTicketService.callTroubleTicket(existingToken);
                        telegramService.sendMessageWithKey(chatId, "TroubleTicketInformation", ticketInfo);
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_SELECT_SERVICE:
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        ServiceListResult services = productService
                                .getMainServices(existingToken, selected.accountId());
                        if (services.hasError()) {
                            userSessionService.clearServices(chatId);
                            telegramService.sendMessageWithKey(chatId, "UnableToRetrieveServices", services.errorMessage());
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                        } else if (services.services().isEmpty()) {
                            userSessionService.clearServices(chatId);
                            telegramService.sendMessageWithKey(chatId, "NoServicesForAccount", selected.accountId());
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                        } else {
                            userSessionService.saveServices(chatId, services.services());
                            if (services.services().size() == 1) {
                                ServiceSummary onlyService = services.services().get(0);
                                userSessionService.selectService(chatId, onlyService);
                                String name = (onlyService.productName() == null || onlyService.productName().isBlank())
                                        ? telegramService.translate(chatId, "UnknownService")
                                        : onlyService.productName().strip();
                                String number = (onlyService.accessNumber() == null || onlyService.accessNumber().isBlank())
                                        ? telegramService.translate(chatId, "NoAccessNumber")
                                        : onlyService.accessNumber().strip();
                                telegramService.sendMessageWithKey(chatId, "ServiceSelected", name, number);
                                telegramService.sendLoggedInMenu(chatId, selected,
                                        userSessionService.getAccounts(chatId).size() > 1);
                            } else {
                                telegramService.sendServicePage(chatId, services.services(), 0);
                            }
                        }
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_MY_ISSUES:
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        ServiceSummary selectedService = userSessionService.getSelectedService(chatId);
                        TroubleTicketListResult result = troubleTicketService
                                .getTroubleTicketsByAccountId(existingToken, selected.accountId(),
                                        selectedService == null ? null : selectedService.productId());
                        if (result.hasError()) {
                            userSessionService.clearTroubleTickets(chatId);
                            telegramService.sendMessageWithKey(chatId, "UnableToRetrieveTroubleTickets", result.errorMessage());
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                        } else if (result.tickets().isEmpty()) {
                            userSessionService.clearTroubleTickets(chatId);
                            telegramService.sendMessageWithKey(chatId, "NoTroubleTicketsForAccount", selected.accountId());
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                        } else {
                            userSessionService.saveTroubleTickets(chatId, result.tickets());
                            telegramService.sendTroubleTicketPage(chatId, result.tickets(), 0);
                        }
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_INVOICE_HISTORY:
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        BusinessMenuItem invoiceMenuItem = menuConfigurationProvider.findMenuItemByCallback(text);
                        if (invoiceMenuItem != null && invoiceMenuItem.isFunctionMenu()) {
                            userSessionService.setInvoiceActionsMenu(chatId, invoiceMenuItem.submenuId());
                        } else {
                            userSessionService.setInvoiceActionsMenu(chatId, null);
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        InvoiceListResult invoices = invoiceService.getInvoices(existingToken, selected.accountId());
                        if (invoices.hasError()) {
                            userSessionService.clearInvoices(chatId);
                            telegramService.sendMessageWithKey(chatId, "UnableToRetrieveInvoices", invoices.errorMessage());
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                        } else if (invoices.invoices().isEmpty()) {
                            userSessionService.clearInvoices(chatId);
                            telegramService.sendMessageWithKey(chatId, "NoInvoicesForAccount", selected.accountId());
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                        } else {
                            userSessionService.saveInvoices(chatId, invoices.invoices());
                            telegramService.sendInvoicePage(chatId, invoices.invoices(), 0);
                        }
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_SELF_SERVICE_LOGIN:
                case "3":
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        String apiResult = troubleTicketService.callTroubleTicket(existingToken);
                        telegramService.sendMessageWithKey(chatId, "UsingExistingLogin", apiResult);
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        String loginUrl = oauthSessionService.buildAuthUrl(chatId);
                        log.info("Login URL for chat {}: {}", chatId, loginUrl);
                        telegramService.sendLoginMenu(chatId, loginUrl);
                        telegramService.sendMessageWithKey(chatId, "TapLoginButton");
                    }
                    break;
                case TelegramService.CALLBACK_DIRECT_LOGIN:
                case "4":
                    // Step 1: Authenticate and get token
                    String token = null;
                    String authMessage;
                    try {
                        token = keycloakAuthService.getAccessToken();
                        authMessage = telegramService.translate(chatId, "AuthOk");
                    } catch (Exception e) {
                        authMessage = telegramService.format(chatId, "AuthError", e.getMessage());
                    }

                    // Step 2: Call the external API using the token
                    String apiResponse = telegramService.translate(chatId, "NoApiResponse");
                    if (token != null) {
                    apiResponse = troubleTicketService.callTroubleTicket(token);
                    }

                    // Step 3: Combine and send to Telegram
                    String externalApiMessage = telegramService.format(chatId, "ExternalApiResult", apiResponse);
                    telegramService.sendMessage(chatId, authMessage + "\n\n" + externalApiMessage);
                    telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                    break;
                case "/start":
                default:
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        ServiceSummary selectedService = userSessionService.getSelectedService(chatId);
                        ServiceFunctionExecutor.ExecutionResult execResult = serviceFunctionExecutor
                                .execute(text, existingToken, selected, selectedService);
                        if (execResult.handled()) {
                            telegramService.sendMessage(chatId, execResult.message());
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                            break;
                        }
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
                    }
            }
        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
        }

        return ResponseEntity.ok().build();
    }

    private String extractDisplayName(Map<String, Object> chat) {
        if (chat == null) {
            return null;
        }
        Object username = chat.get("username");
        if (username instanceof String handle && !handle.isBlank()) {
            return handle.strip();
        }
        Object first = chat.get("first_name");
        Object last = chat.get("last_name");
        StringBuilder name = new StringBuilder();
        if (first instanceof String firstName && !firstName.isBlank()) {
            name.append(firstName.strip());
        }
        if (last instanceof String lastName && !lastName.isBlank()) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(lastName.strip());
        }
        return name.isEmpty() ? null : name.toString();
    }

    private void handleInvoiceAction(long chatId, String invoiceId, String translationKey) {
        InvoiceSummary invoice = findInvoiceById(chatId, invoiceId);
        if (invoice == null) {
            userSessionService.clearInvoices(chatId);
            telegramService.sendMessageWithKey(chatId, "InvoiceNoLongerAvailable");
            return;
        }
        userSessionService.selectInvoice(chatId, invoice);
        telegramService.sendMessageWithKey(chatId, translationKey, invoice.id());
        telegramService.sendInvoiceActions(chatId, invoice);
    }

    private InvoiceSummary findInvoiceById(long chatId, String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return null;
        }
        return userSessionService.getInvoices(chatId).stream()
                .filter(inv -> invoiceId.equals(inv.id()))
                .findFirst()
                .orElse(userSessionService.getSelectedInvoice(chatId));
    }

    private int parseIndex(String text, String prefix) {
        try {
            return Integer.parseInt(text.substring(prefix.length()));
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean ensureAccountSelected(long chatId) {
        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
        if (selected != null) {
            return true;
        }
        var accounts = userSessionService.getAccounts(chatId);
        if (accounts.isEmpty()) {
            telegramService.sendMessageWithKey(chatId, "NoStoredAccounts");
            telegramService.sendLoginMenu(chatId, oauthSessionService.buildAuthUrl(chatId));
            return false;
        }
        telegramService.sendMessageWithKey(chatId, "ChooseAccountToContinue");
        telegramService.sendAccountPage(chatId, accounts, 0);
        return false;
    }
}
