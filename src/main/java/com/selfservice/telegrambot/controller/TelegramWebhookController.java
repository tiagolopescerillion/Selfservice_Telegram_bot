package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.UserSessionService;
import com.selfservice.telegrambot.service.OAuthLoginService;

import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.telegrambot.service.KeycloakAuthService;
import com.selfservice.telegrambot.service.TroubleTicketService;
import com.selfservice.telegrambot.service.MainServiceCatalogService;
import com.selfservice.telegrambot.service.dto.AccountSummary;
import com.selfservice.telegrambot.service.dto.TroubleTicketListResult;
import com.selfservice.telegrambot.service.dto.ServiceListResult;
import com.selfservice.telegrambot.service.dto.ServiceSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.selfservice.telegrambot.service.ExternalApiService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhook/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private final TelegramService telegramService;
    private final KeycloakAuthService keycloakAuthService;
    private final ExternalApiService externalApiService;
    private final MainServiceCatalogService mainServiceCatalogService;
    private final OAuthLoginService oauthLoginService;
    private final UserSessionService userSessionService;
    private final TroubleTicketService troubleTicketService;

    public TelegramWebhookController(TelegramService telegramService,
            KeycloakAuthService keycloakAuthService,
            ExternalApiService externalApiService,
            MainServiceCatalogService mainServiceCatalogService,
            OAuthLoginService oauthLoginService,
            UserSessionService userSessionService,
            TroubleTicketService troubleTicketService) {
        this.telegramService = telegramService;
        this.keycloakAuthService = keycloakAuthService;
        this.externalApiService = externalApiService;
        this.mainServiceCatalogService = mainServiceCatalogService;
        this.oauthLoginService = oauthLoginService;

        this.userSessionService = userSessionService;
        this.troubleTicketService = troubleTicketService;

    }

    @PostMapping
    public ResponseEntity<Void> onUpdate(@RequestBody Map<String, Object> update) {
        log.info("Incoming Telegram update: {}", update);

        try {

            Map<String, Object> message = (Map<String, Object>) update.get("message");
            Map<String, Object> callbackQuery = (Map<String, Object>) update.get("callback_query");
            Map<String, Object> chat;
            String text;
            long chatId;

            if (message != null) {
                chat = (Map<String, Object>) message.get("chat");
                if (chat == null || chat.get("id") == null)
                    return ResponseEntity.ok().build();

                chatId = ((Number) chat.get("id")).longValue();
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
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_SELF_SERVICE_LOGIN))) {
                text = TelegramService.CALLBACK_SELF_SERVICE_LOGIN;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_DIRECT_LOGIN))) {
                text = TelegramService.CALLBACK_DIRECT_LOGIN;
            } else if (text.equals(telegramService.translate(chatId, TelegramService.KEY_BUTTON_LOGOUT))) {
                text = TelegramService.CALLBACK_LOGOUT;
            }

            String existingToken = userSessionService.getValidAccessToken(chatId);
            boolean hasValidToken = existingToken != null;
            String loginReminder = telegramService.format(chatId, "LoginReminder",
                    telegramService.translate(chatId, TelegramService.KEY_BUTTON_SELF_SERVICE_LOGIN));

            if (text.startsWith(TelegramService.CALLBACK_SHOW_MORE_ACCOUNTS_PREFIX)) {
                int offset = parseIndex(text, TelegramService.CALLBACK_SHOW_MORE_ACCOUNTS_PREFIX);
                var accounts = userSessionService.getAccounts(chatId);
                if (accounts.isEmpty()) {
                    telegramService.sendMessageWithKey(chatId, "NoStoredAccounts");
                    telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
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

            if (TelegramService.CALLBACK_LANGUAGE_MENU.equals(text)) {
                telegramService.sendLanguageMenu(chatId);
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
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
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
                    telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (TelegramService.CALLBACK_LOGOUT.equals(text)) {
                String refreshToken = userSessionService.getRefreshToken(chatId);
                String idToken = userSessionService.getIdToken(chatId);
                try {
                    oauthLoginService.logout(refreshToken, idToken);
                } catch (Exception e) {
                    log.warn("Failed to revoke Keycloak session for chat {}", chatId, e);
                }
                userSessionService.clearSession(chatId);
                telegramService.sendMessageWithKey(chatId, "LoggedOutMessage");
                telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_SERVICE_PREFIX)) {
                int index = parseIndex(text, TelegramService.CALLBACK_SERVICE_PREFIX);
                List<ServiceSummary> services = userSessionService.getServices(chatId);
                if (services.isEmpty() || index < 0 || index >= services.size()) {
                    telegramService.sendMessageWithKey(chatId, "ServiceNoLongerAvailable");
                } else {
                    ServiceSummary selectedService = services.get(index);
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
                    telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_ACCOUNT_PREFIX)) {
                int index = parseIndex(text, TelegramService.CALLBACK_ACCOUNT_PREFIX);
                var accounts = userSessionService.getAccounts(chatId);
                if (accounts.isEmpty() || index < 0 || index >= accounts.size()) {
                    telegramService.sendMessageWithKey(chatId, "AccountSelectionExpired");
                    telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
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
                    telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
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
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
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
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
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
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_SELECT_SERVICE:
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        ServiceListResult services = mainServiceCatalogService
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
                            telegramService.sendServicePage(chatId, services.services(), 0);
                        }
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_MY_ISSUES:
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        TroubleTicketListResult result = troubleTicketService
                                .getTroubleTicketsByAccountId(existingToken, selected.accountId());
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
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
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
                        String loginUrl = oauthLoginService.buildAuthUrl(chatId);
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
                        apiResponse = externalApiService.callTroubleTicketApi(token);
                    }

                    // Step 3: Combine and send to Telegram
                    String externalApiMessage = telegramService.format(chatId, "ExternalApiResult", apiResponse);
                    telegramService.sendMessage(chatId, authMessage + "\n\n" + externalApiMessage);
                    telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                    break;
                case "/start":
                default:
                    if (hasValidToken) {
                        if (ensureAccountSelected(chatId)) {
                            AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                        }
                    } else {
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                    }
            }
        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
        }

        return ResponseEntity.ok().build();
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
            telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
            return false;
        }
        telegramService.sendMessageWithKey(chatId, "ChooseAccountToContinue");
        telegramService.sendAccountPage(chatId, accounts, 0);
        return false;
    }
}
