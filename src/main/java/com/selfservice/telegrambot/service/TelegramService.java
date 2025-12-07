package com.selfservice.telegrambot.service;

import com.selfservice.application.config.LoginMenuProperties;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.InvoiceSummary;
import com.selfservice.application.dto.ServiceSummary;
import com.selfservice.application.dto.TroubleTicketSummary;
import com.selfservice.application.config.menu.BusinessMenuConfigurationProvider;
import com.selfservice.application.config.menu.BusinessMenuItem;
import com.selfservice.application.config.menu.LoginMenuFunction;
import com.selfservice.application.config.menu.LoginMenuItem;
import com.selfservice.application.service.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
    public static final String KEY_BUTTON_INVOICE_HISTORY = "ButtonInvoiceHistory";
    public static final String KEY_BUTTON_BACK_TO_MENU = "ButtonBackToMenu";
    public static final String KEY_BUTTON_CHANGE_ACCOUNT = "ButtonChangeAccount";
    public static final String KEY_BUTTON_CHANGE_LANGUAGE = "ButtonChangeLanguage";
    public static final String KEY_BUTTON_MENU = "ButtonMenu";
    public static final String KEY_BUTTON_SETTINGS = "ButtonSettings";
    public static final String KEY_BUTTON_OPT_IN = "ButtonOptIn";
    public static final String KEY_OPT_IN_YES = "OptInYes";
    public static final String KEY_OPT_IN_NO = "OptInNo";
    public static final String KEY_BUTTON_LOGOUT = "ButtonLogout";
    public static final String KEY_BUTTON_BUSINESS_MENU_HOME = "BusinessMenuHome";
    public static final String KEY_BUTTON_BUSINESS_MENU_UP = "BusinessMenuUp";
    public static final String KEY_SHOW_MORE = "ShowMore";
    public static final String KEY_SELECT_ACCOUNT_PROMPT = "SelectAccountPrompt";

    public static final String CALLBACK_SELF_SERVICE_LOGIN = "LOGIN_SELF_SERVICE";
    public static final String CALLBACK_DIRECT_LOGIN = "LOGIN_DIRECT";
    public static final String CALLBACK_HELLO_WORLD = "HELLO_WORLD";
    public static final String CALLBACK_HELLO_CERILLION = "HELLO_CERILLION";
    public static final String CALLBACK_TROUBLE_TICKET = "VIEW_TROUBLE_TICKET";
    public static final String CALLBACK_INVOICE_HISTORY = "INVOICE_HISTORY";
    public static final String CALLBACK_ACCOUNT_PREFIX = "ACCOUNT:";
    public static final String CALLBACK_SERVICE_PREFIX = "SERVICE:";
    public static final String CALLBACK_INVOICE_PREFIX = "INVOICE:";
    public static final String CALLBACK_MY_ISSUES = "MY_ISSUES";
    public static final String CALLBACK_TROUBLE_TICKET_PREFIX = "TICKET:";
    public static final String CALLBACK_SELECT_SERVICE = "SELECT_SERVICE";
    public static final String CALLBACK_CHANGE_ACCOUNT = "CHANGE_ACCOUNT";
    public static final String CALLBACK_LANGUAGE_MENU = "LANGUAGE_MENU";
    public static final String CALLBACK_MENU = "MENU";
    public static final String CALLBACK_SETTINGS_MENU = "SETTINGS_MENU";
    public static final String CALLBACK_LANGUAGE_PREFIX = "LANGUAGE:";
    public static final String CALLBACK_LOGOUT = "LOGOUT";
    public static final String CALLBACK_SHOW_MORE_ACCOUNTS_PREFIX = "SHOW_MORE_ACCOUNTS:";
    public static final String CALLBACK_SHOW_MORE_SERVICES_PREFIX = "SHOW_MORE_SERVICES:";
    public static final String CALLBACK_SHOW_MORE_TICKETS_PREFIX = "SHOW_MORE_TICKETS:";
    public static final String CALLBACK_SHOW_MORE_INVOICES_PREFIX = "SHOW_MORE_INVOICES:";
    public static final String CALLBACK_INVOICE_VIEW_PDF_PREFIX = "INVOICE_VIEW_PDF:";
    public static final String CALLBACK_INVOICE_PAY_PREFIX = "INVOICE_PAY:";
    public static final String CALLBACK_INVOICE_COMPARE_PREFIX = "INVOICE_COMPARE:";
    public static final String CALLBACK_INVOICE_BACK_TO_MENU = "INVOICE_BACK_TO_MENU";
    public static final String CALLBACK_BUSINESS_MENU_HOME = "BUSINESS_MENU_HOME";
    public static final String CALLBACK_BUSINESS_MENU_UP = "BUSINESS_MENU_UP";
    public static final String CALLBACK_BUSINESS_MENU_PREFIX = "BUSINESS_MENU:";
    public static final String CALLBACK_OPT_IN_PROMPT = "OPT_IN_PROMPT";
    public static final String CALLBACK_OPT_IN_ACCEPT = "OPT_IN_ACCEPT";
    public static final String CALLBACK_OPT_IN_DECLINE = "OPT_IN_DECLINE";

    private final RestTemplate rest = new RestTemplate();
    private final String baseUrl;
    private final String publicBaseUrl;
    private final TranslationService translationService;
    private final UserSessionService userSessionService;
    private final BusinessMenuConfigurationProvider menuConfigurationProvider;
    private final LoginMenuProperties loginMenuProperties;

    public TelegramService(
            @Value("${telegram.bot.token}") String token,
            @Value("${app.public-base-url:}") String publicBaseUrl,
            TranslationService translationService,
            UserSessionService userSessionService,
            BusinessMenuConfigurationProvider menuConfigurationProvider,
            LoginMenuProperties loginMenuProperties) {

        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("telegram.bot.token must be configured in telegram-local.yml");
        }

        this.baseUrl = "https://api.telegram.org/bot" + token;
        this.publicBaseUrl = (publicBaseUrl == null) ? "" : publicBaseUrl;
        this.translationService = translationService;
        this.userSessionService = userSessionService;
        this.menuConfigurationProvider = menuConfigurationProvider;
        this.loginMenuProperties = loginMenuProperties;

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

    public List<LoginMenuItem> loginMenuOptions() {
        List<LoginMenuItem> configured = menuConfigurationProvider.getLoginMenuItems();
        List<LoginMenuItem> options = new ArrayList<>();
        for (LoginMenuItem item : configured) {
            LoginMenuFunction function = item.resolvedFunction();
            if (function == LoginMenuFunction.DIGITAL_LOGIN && !loginMenuProperties.isDigitalLoginEnabled()) {
                continue;
            }
            if (function == LoginMenuFunction.CRM_LOGIN && !loginMenuProperties.isCrmLoginEnabled()) {
                continue;
            }
            options.add(item);
        }
        return options;
    }

    public List<LoginMenuItem> loginSettingsMenuOptions() {
        return menuConfigurationProvider.getLoginSettingsMenuItems();
    }

    public LoginMenuItem findLoginMenuItemByCallback(String callbackData) {
        return menuConfigurationProvider.findLoginMenuItemByCallback(callbackData);
    }

    private String resolveLoginMenuLabel(long chatId, LoginMenuItem item) {
        String translationKey = item.getTranslationKey();
        if (translationKey != null
                && !translationKey.isBlank()
                && translationService.hasTranslation(language(chatId), translationKey)) {
            return translate(chatId, translationKey);
        }
        if (item.getLabel() != null && !item.getLabel().isBlank()) {
            return item.getLabel();
        }
        LoginMenuFunction function = item.resolvedFunction();
        return function == null ? "" : function.name();
    }

    private String resolveLoginCallback(LoginMenuItem item) {
        if (item.getCallbackData() != null && !item.getCallbackData().isBlank()) {
            return item.getCallbackData();
        }
        LoginMenuFunction function = item.resolvedFunction();
        if (function == LoginMenuFunction.DIGITAL_LOGIN) {
            return CALLBACK_SELF_SERVICE_LOGIN;
        }
        if (function == LoginMenuFunction.CRM_LOGIN) {
            return CALLBACK_DIRECT_LOGIN;
        }
        if (function == LoginMenuFunction.OPT_IN) {
            return CALLBACK_OPT_IN_PROMPT;
        }
        if (function == LoginMenuFunction.CHANGE_LANGUAGE) {
            return CALLBACK_LANGUAGE_MENU;
        }
        if (function == LoginMenuFunction.SETTINGS) {
            return CALLBACK_SETTINGS_MENU;
        }
        if (function == LoginMenuFunction.MENU) {
            return CALLBACK_MENU;
        }
        return CALLBACK_MENU;
    }

    public void sendLoginMenu(long chatId, String loginUrl) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        List<LoginMenuItem> options = loginMenuOptions();
        for (LoginMenuItem option : options) {
            LoginMenuFunction function = option.resolvedFunction();
            if (function == LoginMenuFunction.DIGITAL_LOGIN && loginUrl != null && !loginUrl.isBlank()) {
                keyboard.add(List.of(Map.of(
                        "text", resolveLoginMenuLabel(chatId, option),
                        "url", loginUrl)));
                continue;
            }
            keyboard.add(List.of(Map.of(
                    "text", resolveLoginMenuLabel(chatId, option),
                    "callback_data", resolveLoginCallback(option))));
        }

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", translate(chatId, "PleaseChooseSignIn"),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendOptInPrompt(long chatId) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                Map.of("text", translate(chatId, KEY_OPT_IN_YES), "callback_data", CALLBACK_OPT_IN_ACCEPT),
                Map.of("text", translate(chatId, KEY_OPT_IN_NO), "callback_data", CALLBACK_OPT_IN_DECLINE)));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_MENU),
                "callback_data", CALLBACK_MENU)));

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", translate(chatId, "OptInPrompt"),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendLoggedInMenu(long chatId, AccountSummary selectedAccount, boolean showChangeAccountOption) {
        sendLoggedInMenu(chatId, selectedAccount, showChangeAccountOption, null);
    }

    public void sendLoggedInMenu(long chatId, AccountSummary selectedAccount, boolean showChangeAccountOption, String greeting) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        String menuId = resolveCurrentMenuId(chatId);
        List<BusinessMenuItem> menuItems = menuConfigurationProvider.getMenuItems(menuId);
        for (BusinessMenuItem item : menuItems) {
            if (item.isSubMenu() && !menuConfigurationProvider.menuExists(item.submenuId())) {
                log.warn("Chat {} attempted to render missing submenu {}", chatId, item.submenuId());
                continue;
            }

            Map<String, Object> button = new HashMap<>();
            button.put("text", resolveMenuLabel(chatId, item));

            if (item.isWeblink()) {
                String resolvedUrl = resolveWeblinkUrl(chatId, item);
                if (resolvedUrl == null || resolvedUrl.isBlank()) {
                    log.warn("Chat {} skipped weblink {} because no URL was provided", chatId, item.weblink());
                    continue;
                }
                button.put("url", resolvedUrl);
            } else {
                button.put("callback_data", resolveCallback(item));
            }

            keyboard.add(List.of(button));
        }

        int depth = userSessionService.getBusinessMenuDepth(chatId, menuConfigurationProvider.getRootMenuId());
        if (depth >= 1) {
            List<Map<String, Object>> navigationRow = new ArrayList<>();
            navigationRow.add(Map.of(
                    "text", translate(chatId, KEY_BUTTON_BUSINESS_MENU_HOME),
                    "callback_data", CALLBACK_BUSINESS_MENU_HOME));
            if (depth >= 2) {
                navigationRow.add(Map.of(
                        "text", translate(chatId, KEY_BUTTON_BUSINESS_MENU_UP),
                        "callback_data", CALLBACK_BUSINESS_MENU_UP));
            }
            keyboard.add(navigationRow);
        }
        if (showChangeAccountOption) {
            keyboard.add(List.of(Map.of(
                    "text", translate(chatId, KEY_BUTTON_CHANGE_ACCOUNT),
                    "callback_data", CALLBACK_CHANGE_ACCOUNT)));
        }
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_SETTINGS),
                "callback_data", CALLBACK_SETTINGS_MENU)));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_LOGOUT),
                "callback_data", CALLBACK_LOGOUT)));

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

        StringBuilder menuText = new StringBuilder();
        appendParagraph(menuText, greeting);
        if (selectedAccount != null) {
            appendParagraph(menuText, format(chatId, "AccountSelected", selectedAccount.accountId()));
        }
        appendParagraph(menuText, translate(chatId, "LoginWelcome"));

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
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, "LanguagePortuguese"),
                "callback_data", CALLBACK_LANGUAGE_PREFIX + "pt")));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, "LanguageRussian"),
                "callback_data", CALLBACK_LANGUAGE_PREFIX + "ru")));
        keyboard.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_MENU),
                "callback_data", CALLBACK_MENU)));

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", translate(chatId, "ChooseLanguagePrompt"),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendSettingsMenu(long chatId) {
        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<LoginMenuItem> options = loginSettingsMenuOptions();
        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        for (LoginMenuItem option : options) {
            keyboard.add(List.of(Map.of(
                    "text", resolveLoginMenuLabel(chatId, option),
                    "callback_data", resolveLoginCallback(option))));
        }

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", translate(chatId, "SettingsMenuPrompt"),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    private String resolveMenuLabel(long chatId, BusinessMenuItem item) {
        String translationKey = item.translationKey();
        if (translationKey != null
                && !translationKey.isBlank()
                && translationService.hasTranslation(language(chatId), translationKey)) {
            return translate(chatId, translationKey);
        }
        if (item.label() != null && !item.label().isBlank()) {
            return item.label();
        }
        if (item.isWeblink() && item.weblink() != null && !item.weblink().isBlank()) {
            return item.weblink();
        }
        return item.function();
    }

    private String resolveCallback(BusinessMenuItem item) {
        if (item.isWeblink()) {
            return null;
        }
        if (item.callbackData() != null && !item.callbackData().isBlank()) {
            return item.callbackData();
        }
        if (item.isSubMenu()) {
            return CALLBACK_BUSINESS_MENU_PREFIX + item.submenuId();
        }
        return item.function();
    }

    private String resolveWeblinkUrl(long chatId, BusinessMenuItem item) {
        if (item == null || !StringUtils.hasText(item.url())) {
            return null;
        }

        String contextualUrl = applyContextualPath(chatId, item);
        if (!item.isAuthenticatedLink()) {
            return contextualUrl;
        }

        String exchangeId = userSessionService.getExchangeId(chatId);
        if (!StringUtils.hasText(exchangeId)) {
            return contextualUrl;
        }
        try {
            return UriComponentsBuilder.fromUriString(contextualUrl)
                    .queryParam("exchangeId", exchangeId)
                    .build(true)
                    .toUriString();
        } catch (Exception ex) {
            log.warn("Failed to append exchangeId to weblink {}: {}", contextualUrl, ex.getMessage());
            String encoded = UriUtils.encode(exchangeId, StandardCharsets.UTF_8);
            return contextualUrl + (contextualUrl.contains("?") ? "&" : "?") + "exchangeId=" + encoded;
        }
    }

    private String applyContextualPath(long chatId, BusinessMenuItem item) {
        if (item.requiresAccountContext()) {
            AccountSummary account = userSessionService.getSelectedAccount(chatId);
            if (account == null || !StringUtils.hasText(account.accountId())) {
                log.warn("No selected account found for contextual weblink {}", item.url());
                return item.url();
            }
            try {
                return UriComponentsBuilder.fromUriString(item.url())
                        .pathSegment(account.accountId())
                        .build(true)
                        .toUriString();
            } catch (Exception ex) {
                log.warn("Failed to append account {} to weblink {}: {}", account.accountId(), item.url(), ex.getMessage());
                String encoded = UriUtils.encodePathSegment(account.accountId(), StandardCharsets.UTF_8);
                return item.url().endsWith("/") ? item.url() + encoded : item.url() + "/" + encoded;
            }
        }

        if (item.requiresServiceContext()) {
            ServiceSummary service = userSessionService.getSelectedService(chatId);
            if (service == null || !StringUtils.hasText(service.productId())) {
                log.warn("No selected service found for contextual weblink {}", item.url());
                return item.url();
            }
            try {
                return UriComponentsBuilder.fromUriString(item.url())
                        .pathSegment(service.productId())
                        .build(true)
                        .toUriString();
            } catch (Exception ex) {
                log.warn("Failed to append service {} to weblink {}: {}", service.productId(), item.url(), ex.getMessage());
                String encoded = UriUtils.encodePathSegment(service.productId(), StandardCharsets.UTF_8);
                return item.url().endsWith("/") ? item.url() + encoded : item.url() + "/" + encoded;
            }
        }

        return item.url();
    }

    private String resolveCurrentMenuId(long chatId) {
        String menuId = userSessionService.currentBusinessMenu(chatId, menuConfigurationProvider.getRootMenuId());
        if (!menuConfigurationProvider.menuExists(menuId)) {
            log.warn("Chat {} had stale menu id {}, resetting to root", chatId, menuId);
            userSessionService.resetBusinessMenu(chatId, menuConfigurationProvider.getRootMenuId());
            menuId = menuConfigurationProvider.getRootMenuId();
        }
        return menuId;
    }

    public boolean goToBusinessMenu(long chatId, String menuId) {
        if (menuId == null || menuId.isBlank() || !menuConfigurationProvider.menuExists(menuId)) {
            return false;
        }
        userSessionService.enterBusinessMenu(chatId, menuId, menuConfigurationProvider.getRootMenuId());
        return true;
    }

    public void goHomeBusinessMenu(long chatId) {
        userSessionService.resetBusinessMenu(chatId, menuConfigurationProvider.getRootMenuId());
    }

    public boolean goUpBusinessMenu(long chatId) {
        return userSessionService.goUpBusinessMenu(chatId, menuConfigurationProvider.getRootMenuId());
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
        sendAccountPage(chatId, accounts, startIndex, null);
    }

    public void sendAccountPage(long chatId, List<AccountSummary> accounts, int startIndex, String header) {
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
            if (currentRow.size() == 2) {
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
                    "callback_data", CALLBACK_SHOW_MORE_ACCOUNTS_PREFIX + end)));
        }

        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", rows);

        String prompt = buildPagedPrompt(chatId, KEY_SELECT_ACCOUNT_PROMPT, safeStart, end, accounts.size());
        if (header != null && !header.isBlank()) {
            prompt = header.strip() + "\n\n" + prompt;
        }

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", prompt,
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendServiceCards(long chatId, List<ServiceSummary> services) {
        if (services == null || services.isEmpty()) {
            sendMessageWithKey(chatId, "NoServicesFound");
            return;
        }

        sendServicePage(chatId, services, 0);
    }

    public void sendServicePage(long chatId, List<ServiceSummary> services, int startIndex) {
        Objects.requireNonNull(services, "services must not be null");

        if (services.isEmpty()) {
            sendMessageWithKey(chatId, "NoServicesFound");
            return;
        }

        int safeStart = Math.max(0, startIndex);
        if (safeStart >= services.size()) {
            safeStart = Math.max(0, services.size() - 5);
        }

        int end = Math.min(services.size(), safeStart + 5);

        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> currentRow = new ArrayList<>();
        for (int i = safeStart; i < end; i++) {
            ServiceSummary service = services.get(i);
            String name = (service.productName() == null || service.productName().isBlank())
                    ? translate(chatId, "UnknownService")
                    : service.productName().strip();
            String number = (service.accessNumber() == null || service.accessNumber().isBlank())
                    ? translate(chatId, "NoAccessNumber")
                    : service.accessNumber().strip();

            String buttonText = format(chatId, "ServiceButtonLabel", name, number);
            currentRow.add(Map.of(
                    "text", buttonText,
                    "callback_data", CALLBACK_SERVICE_PREFIX + i));
            if (currentRow.size() == 2) {
                rows.add(List.copyOf(currentRow));
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(List.copyOf(currentRow));
        }

        if (end < services.size()) {
            rows.add(List.of(Map.of(
                    "text", translate(chatId, KEY_SHOW_MORE),
                    "callback_data", CALLBACK_SHOW_MORE_SERVICES_PREFIX + end)));
        }

        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", rows);
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", buildPagedPrompt(chatId, "SelectServicePrompt", safeStart, end, services.size()),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendInvoicePage(long chatId, List<InvoiceSummary> invoices, int startIndex) {
        Objects.requireNonNull(invoices, "invoices must not be null");

        if (invoices.isEmpty()) {
            sendMessageWithKey(chatId, "NoInvoicesAvailable");
            return;
        }

        int safeStart = Math.max(0, startIndex);
        if (safeStart >= invoices.size()) {
            safeStart = Math.max(0, invoices.size() - 5);
        }

        int end = Math.min(invoices.size(), safeStart + 5);

        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> currentRow = new ArrayList<>();
        for (int i = safeStart; i < end; i++) {
            InvoiceSummary invoice = invoices.get(i);
            String label = format(chatId, "InvoiceButtonLabel", safeValue(chatId, invoice.id()),
                    safeValue(chatId, invoice.billDate()), safeValue(chatId, invoice.totalAmount()),
                    safeValue(chatId, invoice.unpaidAmount()));
            currentRow.add(Map.of(
                    "text", label,
                    "callback_data", CALLBACK_INVOICE_PREFIX + i));
            if (currentRow.size() == 2) {
                rows.add(List.copyOf(currentRow));
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(List.copyOf(currentRow));
        }

        if (end < invoices.size()) {
            rows.add(List.of(Map.of(
                    "text", translate(chatId, KEY_SHOW_MORE),
                    "callback_data", CALLBACK_SHOW_MORE_INVOICES_PREFIX + end)));
        }

        rows.add(List.of(Map.of(
                "text", translate(chatId, KEY_BUTTON_BACK_TO_MENU),
                "callback_data", CALLBACK_INVOICE_BACK_TO_MENU)));

        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", rows);
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", buildPagedPrompt(chatId, "SelectInvoicePrompt", safeStart, end, invoices.size()),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendInvoiceActions(long chatId, InvoiceSummary invoice) {
        if (invoice == null || invoice.id() == null || invoice.id().isBlank()) {
            sendMessageWithKey(chatId, "InvoiceNoLongerAvailable");
            return;
        }

        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        List<BusinessMenuItem> actions = menuConfigurationProvider.getMenuItems(
                userSessionService.getInvoiceActionsMenu(chatId));
        if (actions.isEmpty()) {
            actions = List.of(
                    new BusinessMenuItem(1, translate(chatId, "ButtonInvoiceViewPdf"), CALLBACK_INVOICE_VIEW_PDF_PREFIX,
                            CALLBACK_INVOICE_VIEW_PDF_PREFIX, null, null, null, null, null, null),
                    new BusinessMenuItem(2, translate(chatId, "ButtonInvoicePay"), CALLBACK_INVOICE_PAY_PREFIX,
                            CALLBACK_INVOICE_PAY_PREFIX, null, null, null, null, null, null),
                    new BusinessMenuItem(3, translate(chatId, "ButtonInvoiceCompare"), CALLBACK_INVOICE_COMPARE_PREFIX,
                            CALLBACK_INVOICE_COMPARE_PREFIX, null, null, null, null, null, null),
                    new BusinessMenuItem(4, translate(chatId, KEY_BUTTON_BACK_TO_MENU), CALLBACK_INVOICE_BACK_TO_MENU,
                            CALLBACK_INVOICE_BACK_TO_MENU, null, null, null, null, null, null));
        }

        for (BusinessMenuItem action : actions) {
            Map<String, Object> button = new HashMap<>();
            button.put("text", resolveMenuLabel(chatId, action));
            if (action.isWeblink()) {
                String resolvedUrl = resolveWeblinkUrl(chatId, action);
                if (resolvedUrl == null || resolvedUrl.isBlank()) {
                    continue;
                }
                button.put("url", resolvedUrl);
            } else {
                String callback = resolveCallback(action);
                if (callback != null && callback.endsWith(":")) {
                    callback = callback + invoice.id();
                }
                button.put("callback_data", callback);
            }
            keyboard.add(List.of(button));
        }

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", format(chatId, "InvoiceActionsPrompt", invoice.id()),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    public void sendTroubleTicketCards(long chatId, List<TroubleTicketSummary> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            sendMessageWithKey(chatId, "NoTroubleTickets");
            return;
        }

        sendTroubleTicketPage(chatId, tickets, 0);
    }

    public void sendTroubleTicketPage(long chatId, List<TroubleTicketSummary> tickets, int startIndex) {
        Objects.requireNonNull(tickets, "tickets must not be null");

        if (tickets.isEmpty()) {
            sendMessageWithKey(chatId, "NoTroubleTickets");
            return;
        }

        int safeStart = Math.max(0, startIndex);
        if (safeStart >= tickets.size()) {
            safeStart = Math.max(0, tickets.size() - 5);
        }

        int end = Math.min(tickets.size(), safeStart + 5);

        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> currentRow = new ArrayList<>();
        for (int i = safeStart; i < end; i++) {
            TroubleTicketSummary ticket = tickets.get(i);
            String status = (ticket.status() == null || ticket.status().isBlank())
                    ? translate(chatId, "UnknownValue")
                    : ticket.status().strip();
            currentRow.add(Map.of(
                    "text", format(chatId, "TicketButtonLabel", ticket.id(), status),
                    "callback_data", CALLBACK_TROUBLE_TICKET_PREFIX + ticket.id()));
            if (currentRow.size() == 2) {
                rows.add(List.copyOf(currentRow));
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(List.copyOf(currentRow));
        }

        if (end < tickets.size()) {
            rows.add(List.of(Map.of(
                    "text", translate(chatId, KEY_SHOW_MORE),
                    "callback_data", CALLBACK_SHOW_MORE_TICKETS_PREFIX + end)));
        }

        String url = baseUrl + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> replyMarkup = Map.of("inline_keyboard", rows);
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", buildPagedPrompt(chatId, "SelectTicketPrompt", safeStart, end, tickets.size()),
                "reply_markup", replyMarkup);

        post(url, body, headers);
    }

    private void appendParagraph(StringBuilder target, String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        if (target.length() > 0) {
            target.append("\n\n");
        }
        target.append(trimmed);
    }

    private String buildPagedPrompt(long chatId, String promptKey, int start, int end, int total) {
        String prompt = translate(chatId, promptKey);
        if (total > 5) {
            prompt += " " + format(chatId, "ListPageCounter", start + 1, end, total);
        }
        return prompt;
    }

    private String safeValue(long chatId, String value) {
        if (value == null || value.isBlank()) {
            return translate(chatId, "UnknownValue");
        }
        return value.strip();
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
