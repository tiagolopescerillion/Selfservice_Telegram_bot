package com.selfservice.telegrambot.service;

import com.selfservice.telegrambot.service.dto.AccountSummary;
import com.selfservice.telegrambot.service.dto.ServiceSummary;
import com.selfservice.telegrambot.service.dto.TroubleTicketSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TelegramService {
    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    public static final String KEY_BUTTON_SELF_SERVICE_LOGIN = "ButtonSelfServiceLogin";
    public static final String KEY_BUTTON_DIRECT_LOGIN = "ButtonDirectLogin";
    public static final String KEY_BUTTON_HELLO_WORLD = "ButtonHelloWorld";
    public static final String KEY_BUTTON_HELLO_CERILLION = "ButtonHelloCerillion";
    public static final String KEY_BUTTON_TROUBLE_TICKET = "ButtonTroubleTicket";
    public static final String KEY_BUTTON_SELECT_SERVICE = "ButtonSelectService";
    public static final String KEY_BUTTON_MY_ISSUES = "ButtonMyIssues";
    public static final String KEY_BUTTON_CHANGE_ACCOUNT = "ButtonChangeAccount";
    public static final String KEY_BUTTON_CHANGE_LANGUAGE = "ButtonChangeLanguage";
    public static final String KEY_SHOW_MORE = "ShowMore";
    public static final String KEY_SELECT_ACCOUNT_PROMPT = "SelectAccountPrompt";

    public static final String CALLBACK_SELF_SERVICE_LOGIN = "LOGIN_SELF_SERVICE";
    public static final String CALLBACK_DIRECT_LOGIN = "LOGIN_DIRECT";
    public static final String CALLBACK_HELLO_WORLD = "HELLO_WORLD";
    public static final String CALLBACK_HELLO_CERILLION = "HELLO_CERILLION";
    public static final String CALLBACK_TROUBLE_TICKET = "VIEW_TROUBLE_TICKET";
    public static final String CALLBACK_SHOW_MORE_PREFIX = "SHOW_MORE:";
    public static final String CALLBACK_ACCOUNT_PREFIX = "ACCOUNT:";
    public static final String CALLBACK_SERVICE_PREFIX = "SERVICE:";
    public static final String CALLBACK_MY_ISSUES = "MY_ISSUES";
    public static final String CALLBACK_TROUBLE_TICKET_PREFIX = "TICKET:";
    public static final String CALLBACK_SELECT_SERVICE = "SELECT_SERVICE";
    public static final String CALLBACK_CHANGE_ACCOUNT = "CHANGE_ACCOUNT";
    public static final String CALLBACK_LANGUAGE_MENU = "LANGUAGE_MENU";
    public static final String CALLBACK_LANGUAGE_PREFIX = "LANGUAGE:";

    private final RestTemplate rest = new RestTemplate();
    private final String baseUrl;
    private final String publicBaseUrl;
    private final TranslationService translationService;
    private final UserSessionService userSessionService;

    public TelegramService(
            @Value("${telegram.bot.token}") String token,
            @Value("${app.public-base-url:}") String publicBaseUrl,
            TranslationService translationService,
            UserSessionService userSessionService) {

        String nonNullToken = Objects.requireNonNull(
                token, "telegram.bot.token must be set in configuration");

        this.baseUrl = "https://api.telegram.org/bot" + nonNullToken;
        this.publicBaseUrl = (publicBaseUrl == null) ? "" : publicBaseUrl;
        this.translationService = translationService;
        this.userSessionService = userSessionService;

        String masked = this.baseUrl.replaceFirst("/bot[^/]+", "/bot<token>");
        log.info("Telegram baseUrl set to {}", masked);
        if (!this.publicBaseUrl.isBlank()) {
            log.info("Public base URL set to {}", this.publicBaseUrl);
        }
    }

    private String language(long chatId) {
        return userSessionService.getLanguage(chatId);
    }

    public boolean isSupportedLanguage(String language) {
        return translationService.isSupportedLanguage(language);
    }

    public String translate(long chatId, String key) {
        return translationService.get(language(chatId), key);
    }

    public String format(long chatId, String key, Object... args) {
        return translationService.format(language(chatId), key, args);
    }

    public void sendMessageWithKey(long chatId, String key, Object... args) {
        sendMessage(chatId, format(chatId, key, args));
    }

    public void sendMessage(long chatId, String text) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", text);

        post(url, body, headers);
    }

    public void sendLoginMenu(long chatId, String loginUrl) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        if (loginUrl != null && !loginUrl.isBlank()) {
            keyboard.add(List.of(Map.of(
                    "text", translate(chatId, KEY_BUTTON_SELF_SERVICE_LOGIN),
                    "url", loginUrl)));
        } else {
            keyboard.add(List.of(Map.of(
                    "text", translate(chatId, KEY_BUTTON_SELF_SERVICE_LOGIN),
                    "callback_data", CALLBACK_SELF_SERVICE_LOGIN)));
        }
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_DIRECT_LOGIN),
                "callback_data", CALLBACK_DIRECT_LOGIN)));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_CHANGE_LANGUAGE),
                "callback_data", CALLBACK_LANGUAGE_MENU)));

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", translate(chatId, "PleaseChooseSignIn"),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendLoggedInMenu(long chatId, AccountSummary selectedAccount, boolean showChangeAccountOption) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_HELLO_WORLD),
                "callback_data", CALLBACK_HELLO_WORLD)));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_HELLO_CERILLION),
                "callback_data", CALLBACK_HELLO_CERILLION)));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_TROUBLE_TICKET),
                "callback_data", CALLBACK_TROUBLE_TICKET)));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_SELECT_SERVICE),
                "callback_data", CALLBACK_SELECT_SERVICE)));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_MY_ISSUES),
                "callback_data", CALLBACK_MY_ISSUES)));
        if (showChangeAccountOption) {
            keyboard.add(List.of(Map.of(
                    "text", translate(chatId, KEY_BUTTON_CHANGE_ACCOUNT),
                    "callback_data", CALLBACK_CHANGE_ACCOUNT)));
        }

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

        StringBuilder menuText = new StringBuilder(translate(chatId, "LoginWelcome"));
        if (selectedAccount != null) {
            menuText.append("\n").append(format(chatId, "CurrentAccount", selectedAccount.displayLabel()));
        }

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", menuText.toString(),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendLanguageMenu(long chatId) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, "LanguageEnglish"),
                "callback_data", CALLBACK_LANGUAGE_PREFIX + "en")));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, "LanguageFrench"),
                "callback_data", CALLBACK_LANGUAGE_PREFIX + "fr")));

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", translate(chatId, "ChooseLanguagePrompt"),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void answerCallbackQuery(String callbackQueryId) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }

        String url = baseUrl + "/answerCallbackQuery";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("callback_query_id", callbackQueryId);

        post(url, body, headers);
    }

    public String authHelloUrl() {
        return publicBaseUrl.isBlank() ? "/auth/hello" : publicBaseUrl + "/auth/hello";
    }

    public void sendAccountPage(long chatId, List<AccountSummary> accounts, int startIndex) {
        Objects.requireNonNull(accounts, "accounts must not be null");

        if (accounts.isEmpty()) {
            sendMessageWithKey(chatId, "NoAccountsAvailable");
            return;
        }

        int safeStart = Math.max(0, startIndex);
        if (safeStart >= accounts.size()) {
            safeStart = Math.max(0, accounts.size() - 5);
        }

        int end = Math.min(accounts.size(), safeStart + 5);

        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> currentRow = new ArrayList<>();
        for (int i = safeStart; i < end; i++) {
            AccountSummary summary = accounts.get(i);
            currentRow.add(Map.of(
                    "text", summary.displayLabel(),
                    "callback_data", CALLBACK_ACCOUNT_PREFIX + i));
            if (currentRow.size() == 3) {
                rows.add(List.copyOf(currentRow));
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(List.copyOf(currentRow));
        }

        if (end < accounts.size()) {
            rows.add(List.of(Map.of(
                    "text", translate(chatId, KEY_SHOW_MORE),
                    "callback_data", CALLBACK_SHOW_MORE_PREFIX + end)));
        }

        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", rows);
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", translate(chatId, KEY_SELECT_ACCOUNT_PROMPT),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendTroubleTicketCards(long chatId, List<TroubleTicketSummary> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            sendMessageWithKey(chatId, "NoTroubleTickets");
            return;
        }

        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (TroubleTicketSummary ticket : tickets) {
            String status = (ticket.status() == null || ticket.status().isBlank())
                    ? translate(chatId, "UnknownValue")
                    : ticket.status().strip();
            String cardText = format(chatId, "TicketCardText",
                    ticket.id(), status, ticket.descriptionOrFallback());

            List<List<Map<String, Object>>> keyboard = List.of(List.of(Map.of(
                    "text", format(chatId, "TicketSelectButton", ticket.id()),
                    "callback_data", CALLBACK_TROUBLE_TICKET_PREFIX + ticket.id())));

            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
                    "text", cardText,
                    "reply_markup", Map.of("inline_keyboard", keyboard));

            post(url, body, headers);
        }
    }

    public void sendServiceCards(long chatId, List<ServiceSummary> services) {
        if (services == null || services.isEmpty()) {
            sendMessageWithKey(chatId, "NoServicesFound");
            return;
        }

        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (int i = 0; i < services.size(); i++) {
            var service = services.get(i);
            String name = (service.productName() == null || service.productName().isBlank())
                    ? translate(chatId, "UnknownService")
                    : service.productName().strip();
            String number = (service.accessNumber() == null || service.accessNumber().isBlank())
                    ? translate(chatId, "NoAccessNumber")
                    : service.accessNumber().strip();

            String cardText = format(chatId, "ServiceCardText", name, number);

            List<List<Map<String, Object>>> keyboard = List.of(List.of(Map.of(
                    "text", number,
                    "callback_data", CALLBACK_SERVICE_PREFIX + i)));

            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
                    "text", cardText,
                    "reply_markup", Map.of("inline_keyboard", keyboard));

            post(url, body, headers);
        }
    }

    private void post(String url, Map<String, Object> body, HttpHeaders headers) {
        Objects.requireNonNull(url, "url must not be null");
        if (headers == null)
            headers = new HttpHeaders();

        try {
            ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(body, headers), String.class);

            String respBody = (resp.hasBody() && resp.getBody() != null) ? resp.getBody() : "<no-body>";
            log.info("Telegram API OK status={} body={}", resp.getStatusCode().value(), respBody);

        } catch (HttpStatusCodeException ex) {
            String errBody = ex.getResponseBodyAsString();
            if (errBody == null || errBody.isBlank())
                errBody = "<no-body>";
            log.error("Telegram API HTTP {} -> {}", ex.getStatusCode().value(), errBody, ex);

        } catch (Exception ex) {
            log.error("Telegram API call failed", ex);
        }
    }
}
