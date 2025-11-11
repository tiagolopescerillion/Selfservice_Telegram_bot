package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.UserSessionService;
import com.selfservice.telegrambot.service.OAuthLoginService;

import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.telegrambot.service.KeycloakAuthService;
import com.selfservice.telegrambot.service.TroubleTicketService;
import com.selfservice.telegrambot.service.dto.AccountSummary;
import com.selfservice.telegrambot.service.dto.TroubleTicketListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.selfservice.telegrambot.service.ExternalApiService;

import java.util.Map;

@RestController
@RequestMapping("/webhook/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private final TelegramService telegramService;
    private final KeycloakAuthService keycloakAuthService;
    private final ExternalApiService externalApiService;
    private final OAuthLoginService oauthLoginService;
    private final UserSessionService userSessionService;
    private final TroubleTicketService troubleTicketService;

    public TelegramWebhookController(TelegramService telegramService,
            KeycloakAuthService keycloakAuthService,
            ExternalApiService externalApiService,
            OAuthLoginService oauthLoginService,
            UserSessionService userSessionService,
            TroubleTicketService troubleTicketService) {
        this.telegramService = telegramService;
        this.keycloakAuthService = keycloakAuthService;
        this.externalApiService = externalApiService;
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

            String existingToken = userSessionService.getValidAccessToken(chatId);
            boolean hasValidToken = existingToken != null;
            String loginReminder = "Please login using the \"" + TelegramService.BUTTON_SELF_SERVICE_LOGIN
                    + "\" button to access this feature.";

            if (text.startsWith(TelegramService.CALLBACK_SHOW_MORE_PREFIX)) {
                int offset = parseIndex(text, TelegramService.CALLBACK_SHOW_MORE_PREFIX);
                var accounts = userSessionService.getAccounts(chatId);
                if (accounts.isEmpty()) {
                    telegramService.sendMessage(chatId, "No stored accounts. Please login again.");
                    telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                } else {
                    telegramService.sendAccountPage(chatId, accounts, offset);
                }
                return ResponseEntity.ok().build();
            }

            if (text.startsWith(TelegramService.CALLBACK_TROUBLE_TICKET_PREFIX)) {
                String ticketId = text.substring(TelegramService.CALLBACK_TROUBLE_TICKET_PREFIX.length()).trim();
                if (ticketId.isEmpty()) {
                    telegramService.sendMessage(chatId, "That ticket is no longer available.");
                } else {
                    telegramService.sendMessage(chatId, "Ticket selected: #" + ticketId);
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
                    telegramService.sendMessage(chatId, "Account selection expired. Please login again.");
                    telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                } else {
                    var selected = accounts.get(index);
                    userSessionService.selectAccount(chatId, selected);
                    telegramService.sendMessage(chatId,
                            "Selected account: " + selected.displayLabel());
                    telegramService.sendLoggedInMenu(chatId, selected, accounts.size() > 1);
                }
                return ResponseEntity.ok().build();
            }

            if (TelegramService.CALLBACK_CHANGE_ACCOUNT.equals(text)) {
                var accounts = userSessionService.getAccounts(chatId);
                if (accounts.isEmpty()) {
                    userSessionService.clearSelectedAccount(chatId);
                    telegramService.sendMessage(chatId, "No stored accounts. Please login again.");
                    telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                } else {
                    userSessionService.clearSelectedAccount(chatId);
                    telegramService.sendMessage(chatId, "Please choose an account to continue.");
                    telegramService.sendAccountPage(chatId, accounts, 0);
                }
                return ResponseEntity.ok().build();
            }

            switch (text) {
                case TelegramService.CALLBACK_HELLO_WORLD:
                case TelegramService.BUTTON_HELLO_WORLD:
                case "1":
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        telegramService.sendMessage(chatId, "Hello World 👋");
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_HELLO_CERILLION:
                case TelegramService.BUTTON_HELLO_CERILLION:
                case "2":
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        telegramService.sendMessage(chatId, "Hello Cerillion 🚀");
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_TROUBLE_TICKET:
                case TelegramService.BUTTON_TROUBLE_TICKET:
                case "5":
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        String ticketInfo = troubleTicketService.callTroubleTicket(existingToken);
                        telegramService.sendMessage(chatId,
                                "🎫 Trouble ticket information:\n" + ticketInfo);
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_MY_ISSUES:
                case TelegramService.BUTTON_MY_ISSUES:
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        TroubleTicketListResult result = troubleTicketService
                                .getTroubleTicketsByAccountId(existingToken, selected.accountId());
                        if (result.hasError()) {
                            telegramService.sendMessage(chatId,
                                    "Unable to retrieve trouble tickets: " + result.errorMessage());
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                        } else if (result.tickets().isEmpty()) {
                            telegramService.sendMessage(chatId,
                                    "No trouble tickets were found for account " + selected.accountId() + ".");
                            telegramService.sendLoggedInMenu(chatId, selected,
                                    userSessionService.getAccounts(chatId).size() > 1);
                        } else {
                            telegramService.sendMessage(chatId,
                                    "Select a ticket below to continue.");
                            telegramService.sendTroubleTicketCards(chatId, result.tickets());
                        }
                    } else {
                        telegramService.sendMessage(chatId, loginReminder);
                        telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
                    }
                    break;
                case TelegramService.CALLBACK_SELF_SERVICE_LOGIN:
                case TelegramService.BUTTON_SELF_SERVICE_LOGIN:
                case "3":
                    if (hasValidToken) {
                        if (!ensureAccountSelected(chatId)) {
                            break;
                        }
                        AccountSummary selected = userSessionService.getSelectedAccount(chatId);
                        String apiResult = troubleTicketService.callTroubleTicket(existingToken);
                        telegramService.sendMessage(chatId, "Using existing login ✅\n\nAPIMAN trouble ticket result:\n" + apiResult);
                        telegramService.sendLoggedInMenu(chatId, selected,
                                userSessionService.getAccounts(chatId).size() > 1);
                    } else {
                        String loginUrl = oauthLoginService.buildAuthUrl(chatId);
                        log.info("Login URL for chat {}: {}", chatId, loginUrl);
                        telegramService.sendLoginMenu(chatId, loginUrl);
                        telegramService.sendMessage(chatId, "Tap the login button above to continue.");
                    }
                    break;
                case TelegramService.CALLBACK_DIRECT_LOGIN:
                case TelegramService.BUTTON_DIRECT_LOGIN:
                case "4":
                    // Step 1: Authenticate and get token
                    String token = null;
                    String authMessage;
                    try {
                        token = keycloakAuthService.getAccessToken();
                        authMessage = "Auth OK ✅\nToken retrieved successfully.";
                    } catch (Exception e) {
                        authMessage = "Auth ERROR ❌: " + e.getMessage();
                    }

                    // Step 2: Call the external API using the token
                    String apiResponse = "No API response.";
                    if (token != null) {
                        apiResponse = externalApiService.callTroubleTicketApi(token);
                    }

                    // Step 3: Combine and send to Telegram
                    telegramService.sendMessage(chatId,
                            authMessage + "\n\n" +
                                    "External API result:\n" + apiResponse);
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
            telegramService.sendMessage(chatId, "No stored accounts. Please login again.");
            telegramService.sendLoginMenu(chatId, oauthLoginService.buildAuthUrl(chatId));
            return false;
        }
        telegramService.sendMessage(chatId, "Please select an account to continue.");
        telegramService.sendAccountPage(chatId, accounts, 0);
        return false;
    }
}
