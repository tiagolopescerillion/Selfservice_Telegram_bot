package com.selfservice.whatsapp.service;

import com.selfservice.application.config.LoginMenuProperties;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.InvoiceSummary;
import com.selfservice.application.dto.ServiceSummary;
import com.selfservice.application.dto.TroubleTicketSummary;
import com.selfservice.application.config.menu.BusinessMenuConfigurationProvider;
import com.selfservice.application.config.menu.BusinessMenuItem;
import com.selfservice.application.config.menu.LoginMenuDefinition;
import com.selfservice.application.config.menu.LoginMenuFunction;
import com.selfservice.application.config.menu.LoginMenuItem;
import com.selfservice.application.service.TranslationService;
import com.selfservice.telegrambot.service.TelegramService;
import com.selfservice.whatsapp.config.WhatsappProperties;
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
import java.util.List;
import java.util.Map;

@Service
public class WhatsappService {

    public static final String KEY_OPT_IN_YES = "OptInYes";
    public static final String KEY_OPT_IN_NO = "OptInNo";
    public static final String KEY_BUTTON_MENU = "ButtonMenu";


    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);

    public static final String COMMAND_CHANGE_LANGUAGE = "language";
    public static final String COMMAND_LOGOUT = "logout";
    public static final String COMMAND_CHANGE_ACCOUNT = "change account";
    public static final String COMMAND_HOME = "home";
    public static final String COMMAND_UP = "up";
    public static final String COMMAND_MENU = "menu";

    public static final String INTERACTIVE_ID_DIGITAL_LOGIN = "DIGITAL_LOGIN";
    public static final String INTERACTIVE_ID_CRM_LOGIN = "CRM_LOGIN";
    public static final String INTERACTIVE_ID_CHANGE_LANGUAGE = "CHANGE_LANGUAGE";
    public static final String INTERACTIVE_ID_OPT_IN = "OPT_IN";
    public static final String INTERACTIVE_ID_SETTINGS = "SETTINGS";
    public static final String INTERACTIVE_ID_MENU = "MENU";

    private final RestTemplate restTemplate = new RestTemplate();
    private final String phoneNumberId;
    private final String accessToken;
    private final TranslationService translationService;
    private final WhatsappSessionService sessionService;
    private final BusinessMenuConfigurationProvider menuConfigurationProvider;
    private final LoginMenuProperties loginMenuProperties;
    private final WhatsappProperties whatsappProperties;

    public WhatsappService(
            @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.access-token:}") String accessToken,
            TranslationService translationService,
            WhatsappSessionService sessionService,
            BusinessMenuConfigurationProvider menuConfigurationProvider,
            LoginMenuProperties loginMenuProperties,
            WhatsappProperties whatsappProperties) {
        this.phoneNumberId = phoneNumberId == null ? "" : phoneNumberId.trim();
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.translationService = translationService;
        this.sessionService = sessionService;
        this.menuConfigurationProvider = menuConfigurationProvider;
        this.loginMenuProperties = loginMenuProperties;
        this.whatsappProperties = whatsappProperties;

        if (!this.phoneNumberId.isBlank()) {
            log.info("WhatsApp phone-number-id configured");
        }
    }

    public String language(String userId) {
        return sessionService.getLanguage(userId, translationService.getDefaultLanguage());
    }

    public boolean isSupportedLanguage(String language) {
        return translationService.isSupportedLanguage(language);
    }

    public String translate(String userId, String key) {
        return translationService.get(language(userId), key);
    }

    public String format(String userId, String key, Object... args) {
        return translationService.format(language(userId), key, args);
    }

    public void sendText(String to, String message) {
        if (!isConfigured()) {
            log.warn("WhatsApp messaging is not fully configured; cannot send text");
            return;
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to",           to,
                "type",         "text",
                "text",         Map.of("body", message)
        );

        postToWhatsapp(payload);
    }

    public void sendCardMessage(String to, String message, List<String> buttonLabels) {
        if (buttonLabels == null || buttonLabels.isEmpty()) {
            sendText(to, message);
            return;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 1;
        for (String label : buttonLabels) {
            if (!StringUtils.hasText(label)) {
                continue;
            }
            rows.add(buildListRow("CARD:" + index++, label));
        }

        if (rows.isEmpty()) {
            sendText(to, message);
            return;
        }

        String title = (message == null || message.isBlank()) ? "Select an option" : message;
        sendInteractiveList(to, title, title, rows);
    }

    public List<LoginMenuItem> loginSettingsMenuOptions(String userId) {
        int depth = sessionService.getLoginMenuDepth(userId, menuConfigurationProvider.getLoginRootMenuId());
        return loginSettingsMenuOptions(userId, depth);
    }

    public List<LoginMenuItem> loginSettingsMenuOptions(String userId, int menuDepth) {
        List<LoginMenuItem> configured = menuConfigurationProvider.getLoginSettingsMenuItems();
        List<LoginMenuItem> options = new ArrayList<>();
        for (LoginMenuItem item : configured) {
            if (!shouldDisplayLoginMenuItem(item, userId, menuDepth)) {
                continue;
            }
            options.add(item);
        }
        return options;
    }

    public String resolveLoginMenuLabel(String to, LoginMenuItem item) {
        String translationKey = item.getTranslationKey();
        if (translationKey != null
                && !translationKey.isBlank()
                && translationService.hasTranslation(language(to), translationKey)) {
            return translate(to, translationKey);
        }
        if (item.getLabel() != null && !item.getLabel().isBlank()) {
            return item.getLabel();
        }
        LoginMenuFunction function = item.resolvedFunction();
        return function == null ? "" : function.name();
    }

    public String callbackId(LoginMenuItem item) {
        if (item.getCallbackData() != null && !item.getCallbackData().isBlank()) {
            return item.getCallbackData();
        }
        LoginMenuFunction function = item.resolvedFunction();
        return function == null ? "" : function.name();
    }

    private boolean isLoggedIn(String userId) {
        return sessionService.getTokenSnapshot(userId).state() == WhatsappSessionService.TokenState.VALID;
    }

    private boolean hasAlternateAccount(String userId) {
        AccountSummary selected = sessionService.getSelectedAccount(userId);
        if (selected == null) {
            return false;
        }
        return sessionService.getAccounts(userId).stream()
                .anyMatch(account -> !account.accountId().equals(selected.accountId()));
    }

    private boolean hasFunction(LoginMenuItem item, String expected) {
        if (item == null || expected == null) {
            return false;
        }
        if (expected.equalsIgnoreCase(item.getFunction())) {
            return true;
        }
        return expected.equalsIgnoreCase(item.getCallbackData());
    }

    private boolean shouldDisplayLoginMenuItem(LoginMenuItem item, String userId, int menuDepth) {
        if (hasFunction(item, TelegramService.CALLBACK_LOGOUT) && !isLoggedIn(userId)) {
            return false;
        }
        if (hasFunction(item, TelegramService.CALLBACK_MENU) && menuDepth < 1) {
            return false;
        }
        if (hasFunction(item, TelegramService.CALLBACK_BUSINESS_MENU_UP) && menuDepth < 2) {
            return false;
        }
        if ((hasFunction(item, TelegramService.CALLBACK_CHANGE_ACCOUNT) || hasFunction(item, "CHANGE_ACCOUNT"))
                && !hasAlternateAccount(userId)) {
            return false;
        }
        return true;
    }

    public List<LoginMenuItem> loginMenuOptions(String userId) {
        String menuId = resolveCurrentLoginMenu(userId);
        int menuDepth = sessionService.getLoginMenuDepth(userId, menuConfigurationProvider.getLoginRootMenuId());
        return loginMenuOptions(userId, menuId, menuDepth);
    }

    public List<LoginMenuItem> loginMenuOptions(String userId, String menuId, int menuDepth) {
        List<LoginMenuItem> configured = menuConfigurationProvider.getLoginMenuItems(menuId).stream()
                .map(LoginMenuDefinition::toLoginMenuItem)
                .toList();
        if (configured.isEmpty()) {
            configured = menuConfigurationProvider.getLoginMenuItems();
        }
        List<LoginMenuItem> options = new ArrayList<>();
        for (LoginMenuItem item : configured) {
            LoginMenuFunction function = item.resolvedFunction();
            if (function == LoginMenuFunction.DIGITAL_LOGIN && !loginMenuProperties.isDigitalLoginEnabled()) {
                continue;
            }
            if (function == LoginMenuFunction.CRM_LOGIN && !loginMenuProperties.isCrmLoginEnabled()) {
                continue;
            }
            if (!shouldDisplayLoginMenuItem(item, userId, menuDepth)) {
                continue;
            }
            options.add(item);
        }
        return options;
    }

    public void sendLoginMenu(String to) {
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.NONE);
        List<LoginMenuItem> options = loginMenuOptions(to);

        boolean textSent = false;
        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            sendLoginMenuText(to, options);
            textSent = true;
        }

        boolean cardsSent = false;
        if (whatsappProperties.isInteractiveUxEnabled()) {
            cardsSent = sendLoginMenuCards(to, options);
        }

        if (!cardsSent && !textSent) {
            // Interactive-only mode still needs a visible response if cards cannot be sent.
            sendLoginMenuText(to, options);
        }
    }

    private void sendLoginMenuText(String to, List<LoginMenuItem> options) {
        StringBuilder menu = new StringBuilder();
        int index = 1;
        for (LoginMenuItem option : options) {
            menu.append(index)
                    .append(") ")
                    .append(resolveLoginMenuLabel(to, option))
                    .append("\n");
            index++;
        }
        menu.append("\n").append(translate(to, "PleaseChooseSignIn"));
        sendText(to, menu.toString());
    }

    private boolean sendLoginMenuCards(String to, List<LoginMenuItem> options) {
        if (!isConfigured()) {
            log.warn("WhatsApp messaging is not fully configured; cannot send login menu cards");
            return false;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (LoginMenuItem option : options) {
            rows.add(buildListRow(callbackId(option), resolveLoginMenuLabel(to, option)));
        }

        return sendInteractiveList(to,
                translate(to, "PleaseChooseSignIn"),
                translate(to, "PleaseChooseSignIn"),
                rows);
    }

    public void sendDigitalLoginLink(String to, String loginUrl) {
        if (loginUrl == null || loginUrl.isBlank()) {
            sendText(to, "Login link is not available right now. Please try again later.");
            return;
        }
        sendText(to, format(to, "LoginUrlHint", loginUrl));
    }

    public void sendOptInPrompt(String to) {
        String prompt = translate(to, "OptInPrompt");
        String yes = translate(to, KEY_OPT_IN_YES);
        String no = translate(to, KEY_OPT_IN_NO);
        String backToMenu = translate(to, KEY_BUTTON_MENU);
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.OPT_IN);
        sendText(to, String.format("%s\n\n1) %s\n2) %s\n3) %s", prompt, yes, no, backToMenu));
    }

    public void sendOptInAccepted(String to) {
        sendText(to, translate(to, "OptInAccepted"));
    }

    public void sendOptInDeclined(String to) {
        sendText(to, translate(to, "OptInDeclined"));
    }

    public void sendLanguageMenu(String to) {
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.NONE);
        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            StringBuilder menu = new StringBuilder();
            menu.append(translate(to, "ChooseLanguagePrompt")).append("\n")
                    .append("1) ").append(translate(to, "LanguageEnglish")).append("\n")
                    .append("2) ").append(translate(to, "LanguageFrench")).append("\n")
                    .append("3) ").append(translate(to, "LanguagePortuguese")).append("\n")
                    .append("4) ").append(translate(to, "LanguageRussian"));
            sendText(to, menu.toString());
        }
        if (whatsappProperties.isInteractiveUxEnabled()) {
            sendInteractiveList(to,
                    translate(to, "ChooseLanguagePrompt"),
                    translate(to, "ChooseLanguagePrompt"),
                    List.of(
                            buildListRow("1", translate(to, "LanguageEnglish")),
                            buildListRow("2", translate(to, "LanguageFrench")),
                            buildListRow("3", translate(to, "LanguagePortuguese")),
                            buildListRow("4", translate(to, "LanguageRussian"))
                    ));
        }
    }

    public void sendSettingsMenu(String to) {
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.SETTINGS);
        String settingsMenuId = menuConfigurationProvider.getLoginSettingsMenuId();
        if (settingsMenuId != null) {
            goToLoginMenu(to, settingsMenuId);
        }
        List<LoginMenuItem> options = loginSettingsMenuOptions(to);
        StringBuilder menu = new StringBuilder();
        menu.append(translate(to, "SettingsMenuPrompt")).append("\n\n");
        int index = 1;
        for (LoginMenuItem option : options) {
            menu.append(index)
                    .append(") ")
                    .append(resolveLoginMenuLabel(to, option))
                    .append("\n");
            index++;
        }

        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            sendText(to, menu.toString());
        }

        if (whatsappProperties.isInteractiveUxEnabled()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (LoginMenuItem option : options) {
                rows.add(buildListRow(callbackId(option), resolveLoginMenuLabel(to, option)));
            }
            sendInteractiveList(to,
                    translate(to, "SettingsMenuPrompt"),
                    translate(to, "SettingsMenuPrompt"),
                    rows);
        }
    }

    public void sendLoggedInMenu(String to, AccountSummary selectedAccount, boolean showChangeAccountOption) {
        sendLoggedInMenu(to, selectedAccount, showChangeAccountOption, null);
    }

    public void sendLoggedInMenu(String to, AccountSummary selectedAccount, boolean showChangeAccountOption, String greeting) {
        String menuId = resolveCurrentMenuId(to);
        List<BusinessMenuItem> menuItems = menuConfigurationProvider.getMenuItems(menuId);
        StringBuilder body = new StringBuilder();
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.NONE);
        appendParagraph(body, greeting);
        if (selectedAccount != null) {
            appendParagraph(body, format(to, "AccountSelected", selectedAccount.accountId()));
        }
        appendParagraph(body, translate(to, "LoginWelcome"));
        if (!body.toString().endsWith("\n\n")) {
            body.append("\n");
        }

        int index = 1;
        boolean loggedIn = isLoggedIn(to);
        boolean hasAlternateAccount = hasAlternateAccount(to);
        int menuDepth = sessionService.getBusinessMenuDepth(to, menuConfigurationProvider.getRootMenuId());
        for (BusinessMenuItem item : menuItems) {
            if (!showChangeAccountOption && isChangeAccountItem(item)) {
                continue;
            }
            if (!shouldDisplayBusinessMenuItem(item, menuDepth, hasAlternateAccount, loggedIn)) {
                continue;
            }
            if (item.isSubMenu() && !menuConfigurationProvider.menuExists(item.submenuId())) {
                log.warn("User {} attempted to render missing submenu {}", to, item.submenuId());
                continue;
            }
            body.append(index).append(") ")
                    .append(resolveMenuLabel(to, item))
                    .append("\n");
            index++;
        }

        body.append(translate(to, "WhatsappMenuInstruction"));
        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            sendText(to, body.toString());
        }

        if (whatsappProperties.isInteractiveUxEnabled()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            int rowIndex = 1;
            for (BusinessMenuItem item : menuItems) {
                if (!showChangeAccountOption && isChangeAccountItem(item)) {
                    continue;
                }
                if (!shouldDisplayBusinessMenuItem(item, menuDepth, hasAlternateAccount, loggedIn)) {
                    continue;
                }
                if (item.isSubMenu() && !menuConfigurationProvider.menuExists(item.submenuId())) {
                    continue;
                }
                rows.add(buildListRow(String.valueOf(rowIndex), resolveMenuLabel(to, item)));
                rowIndex++;
            }

            sendInteractiveList(to,
                    translate(to, "LoginWelcome"),
                    translate(to, "WhatsappMenuInstruction"),
                    rows);
        }
    }

    private boolean isChangeAccountItem(BusinessMenuItem item) {
        if (item == null) {
            return false;
        }
        if (TelegramService.CALLBACK_CHANGE_ACCOUNT.equalsIgnoreCase(item.callbackData())) {
            return true;
        }
        return TelegramService.CALLBACK_CHANGE_ACCOUNT.equalsIgnoreCase(item.function());
    }

    private boolean isLogoutItem(BusinessMenuItem item) {
        if (item == null) {
            return false;
        }
        if (TelegramService.CALLBACK_LOGOUT.equalsIgnoreCase(item.callbackData())) {
            return true;
        }
        return TelegramService.CALLBACK_LOGOUT.equalsIgnoreCase(item.function());
    }

    private boolean isBackToMenuItem(BusinessMenuItem item) {
        if (item == null) {
            return false;
        }
        if (TelegramService.CALLBACK_MENU.equalsIgnoreCase(item.callbackData())) {
            return true;
        }
        return TelegramService.CALLBACK_MENU.equalsIgnoreCase(item.function());
    }

    private boolean isMenuUpItem(BusinessMenuItem item) {
        if (item == null) {
            return false;
        }
        if (TelegramService.CALLBACK_BUSINESS_MENU_UP.equalsIgnoreCase(item.callbackData())) {
            return true;
        }
        return TelegramService.CALLBACK_BUSINESS_MENU_UP.equalsIgnoreCase(item.function());
    }

    private boolean shouldDisplayBusinessMenuItem(BusinessMenuItem item, int menuDepth, boolean hasAlternateAccount, boolean loggedIn) {
        if (isLogoutItem(item) && !loggedIn) {
            return false;
        }
        if (isBackToMenuItem(item) && menuDepth < 1) {
            return false;
        }
        if (isMenuUpItem(item) && menuDepth < 2) {
            return false;
        }
        if (isChangeAccountItem(item) && !hasAlternateAccount) {
            return false;
        }
        return true;
    }

    public void sendWeblink(String to, BusinessMenuItem item) {
        if (item == null || item.url() == null || item.url().isBlank()) {
            sendText(to, "Link unavailable.");
            return;
        }

        StringBuilder message = new StringBuilder();
        if (item.label() != null && !item.label().isBlank()) {
            message.append(item.label()).append(": ");
        }
        String resolved = resolveWeblinkUrl(to, item);
        if (!StringUtils.hasText(resolved)) {
            resolved = item.url();
        }
        message.append(resolved);
        sendText(to, message.toString());
    }

    public void sendAccountPage(String to, List<AccountSummary> accounts, int startIndex) {
        sendAccountPage(to, accounts, startIndex, null);
    }

    public void sendAccountPage(String to, List<AccountSummary> accounts, int startIndex, String header) {
        if (accounts == null || accounts.isEmpty()) {
            sendText(to, translate(to, "NoAccountsAvailable"));
            return;
        }

        int safeStart = Math.max(0, startIndex);
        if (safeStart >= accounts.size()) {
            safeStart = Math.max(0, accounts.size() - 5);
        }
        int end = Math.min(accounts.size(), safeStart + 5);

        StringBuilder body = new StringBuilder();
        if (header != null && !header.isBlank()) {
            appendParagraph(body, header);
        }
        body.append(buildPagedPrompt(to, TelegramKey.SELECT_ACCOUNT_PROMPT.toString(), safeStart, end, accounts.size()))
                .append("\n");
        for (int i = safeStart; i < end; i++) {
            AccountSummary summary = accounts.get(i);
            body.append(i + 1).append(") ").append(summary.displayLabel()).append("\n");
        }
        if (end < accounts.size()) {
            body.append(translate(to, "WhatsappMoreAccountsInstruction").formatted(end + 1));
        }
        body.append(translate(to, "WhatsappAccountSelectionInstruction"));
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.ACCOUNT, safeStart);
        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            sendText(to, body.toString());
        }

        if (whatsappProperties.isInteractiveUxEnabled()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = safeStart; i < end; i++) {
                AccountSummary summary = accounts.get(i);
                rows.add(buildListRow(String.valueOf(i + 1), summary.displayLabel()));
            }
            if (end < accounts.size()) {
                rows.add(buildListRow(String.valueOf(end + 1), "More accounts"));
            }
            sendInteractiveList(to,
                    translate(to, "SelectAccountPrompt"),
                    translate(to, "WhatsappAccountSelectionInstruction"),
                    rows);
        }
    }

    public void sendServicePage(String to, List<ServiceSummary> services, int startIndex) {
        if (services == null || services.isEmpty()) {
            sendText(to, translate(to, "NoServicesFound"));
            return;
        }

        int safeStart = Math.max(0, startIndex);
        if (safeStart >= services.size()) {
            safeStart = Math.max(0, services.size() - 5);
        }
        int end = Math.min(services.size(), safeStart + 5);

        StringBuilder body = new StringBuilder();
        body.append(buildPagedPrompt(to, "SelectServicePrompt", safeStart, end, services.size())).append("\n");
        for (int i = safeStart; i < end; i++) {
            ServiceSummary service = services.get(i);
            String name = (service.productName() == null || service.productName().isBlank())
                    ? translate(to, "UnknownService")
                    : service.productName().strip();
            String number = (service.accessNumber() == null || service.accessNumber().isBlank())
                    ? translate(to, "NoAccessNumber")
                    : service.accessNumber().strip();
            body.append(i + 1).append(") ")
                    .append(format(to, "ServiceButtonLabel", name, number)).append("\n");
        }
        if (end < services.size()) {
            body.append(translate(to, "WhatsappMoreServicesInstruction").formatted(end + 1));
        }
        body.append(translate(to, "WhatsappServiceSelectionInstruction"));
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.SERVICE, safeStart);
        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            sendText(to, body.toString());
        }

        if (whatsappProperties.isInteractiveUxEnabled()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = safeStart; i < end; i++) {
                ServiceSummary service = services.get(i);
                String name = (service.productName() == null || service.productName().isBlank())
                        ? translate(to, "UnknownService")
                        : service.productName().strip();
                String number = (service.accessNumber() == null || service.accessNumber().isBlank())
                        ? translate(to, "NoAccessNumber")
                        : service.accessNumber().strip();
                rows.add(buildListRow(String.valueOf(i + 1), format(to, "ServiceButtonLabel", name, number)));
            }
            if (end < services.size()) {
                rows.add(buildListRow(String.valueOf(end + 1), "More services"));
            }
            sendInteractiveList(to,
                    translate(to, "SelectServicePrompt"),
                    translate(to, "WhatsappServiceSelectionInstruction"),
                    rows);
        }
    }

    public void sendInvoicePage(String to, List<InvoiceSummary> invoices, int startIndex) {
        if (invoices == null || invoices.isEmpty()) {
            sendText(to, translate(to, "NoInvoicesAvailable"));
            return;
        }

        int safeStart = Math.max(0, startIndex);
        if (safeStart >= invoices.size()) {
            safeStart = Math.max(0, invoices.size() - 5);
        }
        int end = Math.min(invoices.size(), safeStart + 5);

        StringBuilder body = new StringBuilder();
        body.append(buildPagedPrompt(to, "SelectInvoicePrompt", safeStart, end, invoices.size())).append("\n");
        for (int i = safeStart; i < end; i++) {
            InvoiceSummary invoice = invoices.get(i);
            String id = safeText(invoice.id(), translate(to, "UnknownValue"));
            String date = safeText(invoice.billDate(), translate(to, "UnknownValue"));
            String total = safeText(invoice.totalAmount(), translate(to, "UnknownValue"));
            String unpaid = safeText(invoice.unpaidAmount(), translate(to, "UnknownValue"));
            body.append(i + 1).append(") ")
                    .append(format(to, "InvoiceButtonLabel", id, date, total, unpaid)).append("\n");
        }
        if (end < invoices.size()) {
            body.append(translate(to, "WhatsappMoreInvoicesInstruction").formatted(end + 1));
        }
        body.append(translate(to, "WhatsappInvoiceSelectionInstruction"));
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.INVOICE, safeStart);
        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            sendText(to, body.toString());
        }

        if (whatsappProperties.isInteractiveUxEnabled()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = safeStart; i < end; i++) {
                InvoiceSummary invoice = invoices.get(i);
                String id = safeText(invoice.id(), translate(to, "UnknownValue"));
                String date = safeText(invoice.billDate(), translate(to, "UnknownValue"));
                String total = safeText(invoice.totalAmount(), translate(to, "UnknownValue"));
                String unpaid = safeText(invoice.unpaidAmount(), translate(to, "UnknownValue"));
                rows.add(buildListRow(String.valueOf(i + 1),
                        format(to, "InvoiceButtonLabel", id, date, total, unpaid)));
            }
            if (end < invoices.size()) {
                rows.add(buildListRow(String.valueOf(end + 1), "More invoices"));
            }
            sendInteractiveList(to,
                    translate(to, "SelectInvoicePrompt"),
                    translate(to, "WhatsappInvoiceSelectionInstruction"),
                    rows);
        }
    }

    public void sendInvoiceActions(String to, InvoiceSummary invoice) {
        if (invoice == null || invoice.id() == null || invoice.id().isBlank()) {
            sendText(to, translate(to, "InvoiceNoLongerAvailable"));
            return;
        }
        List<BusinessMenuItem> actions = invoiceActions(to);
        StringBuilder body = new StringBuilder();
        body.append(format(to, "InvoiceActionsPrompt", invoice.id())).append("\n");
        for (int i = 0; i < actions.size(); i++) {
            body.append(i + 1).append(") ")
                    .append(resolveMenuLabel(to, actions.get(i)))
                    .append("\n");
        }
        body.append(translate(to, "InvoiceActionsInstruction"));

        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.INVOICE_ACTION);

        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            sendText(to, body.toString());
        }

        if (whatsappProperties.isInteractiveUxEnabled()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < actions.size(); i++) {
                rows.add(buildListRow(String.valueOf(i + 1), resolveMenuLabel(to, actions.get(i))));
            }
            sendInteractiveList(to,
                    format(to, "InvoiceActionsPrompt", invoice.id()),
                    translate(to, "InvoiceActionsInstruction"),
                    rows);
        }
    }

    public List<BusinessMenuItem> invoiceActions(String userId) {
        List<BusinessMenuItem> actions = menuConfigurationProvider
                .getMenuItems(sessionService.getInvoiceActionsMenu(userId));
        if (actions.isEmpty()) {
            return List.of(
                    new BusinessMenuItem(null, 1, translate(userId, "ButtonInvoiceViewPdf"),
                            TelegramService.CALLBACK_INVOICE_VIEW_PDF_PREFIX,
                            TelegramService.CALLBACK_INVOICE_VIEW_PDF_PREFIX, null, null, null, null, null, null, null),
                    new BusinessMenuItem(null, 2, translate(userId, "ButtonInvoicePay"),
                            TelegramService.CALLBACK_INVOICE_PAY_PREFIX,
                            TelegramService.CALLBACK_INVOICE_PAY_PREFIX, null, null, null, null, null, null, null),
                    new BusinessMenuItem(null, 3, translate(userId, "ButtonInvoiceCompare"),
                            TelegramService.CALLBACK_INVOICE_COMPARE_PREFIX,
                            TelegramService.CALLBACK_INVOICE_COMPARE_PREFIX, null, null, null, null, null, null, null),
                    new BusinessMenuItem(null, 4, translate(userId, TelegramService.KEY_BUTTON_BACK_TO_MENU),
                            TelegramService.CALLBACK_MENU,
                            TelegramService.CALLBACK_MENU, null, null, null, null, null, null, null));
        }
        return actions;
    }

    public String invoiceActionCallback(String userId, BusinessMenuItem action, InvoiceSummary invoice) {
        if (action == null) {
            return null;
        }
        String callback = action.callbackData();
        if (callback == null || callback.isBlank()) {
            callback = action.function();
        }
        if (callback != null && callback.endsWith(":")) {
            callback = callback + invoice.id();
        }
        return callback;
    }

    public void sendTroubleTicketPage(String to, List<TroubleTicketSummary> tickets, int startIndex) {
        if (tickets == null || tickets.isEmpty()) {
            sendText(to, translate(to, "NoTroubleTickets"));
            return;
        }

        int safeStart = Math.max(0, startIndex);
        if (safeStart >= tickets.size()) {
            safeStart = Math.max(0, tickets.size() - 5);
        }
        int end = Math.min(tickets.size(), safeStart + 5);

        StringBuilder body = new StringBuilder();
        body.append(buildPagedPrompt(to, "SelectTicketPrompt", safeStart, end, tickets.size())).append("\n");
        for (int i = safeStart; i < end; i++) {
            TroubleTicketSummary ticket = tickets.get(i);
            String status = (ticket.status() == null || ticket.status().isBlank())
                    ? translate(to, "UnknownValue")
                    : ticket.status().strip();
            body.append(i + 1).append(") ")
                    .append(format(to, "TicketButtonLabel", ticket.id(), status)).append("\n");
        }
        if (end < tickets.size()) {
            body.append(translate(to, "WhatsappMoreTicketsInstruction").formatted(end + 1));
        }
        body.append(translate(to, "WhatsappTicketSelectionInstruction"));
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.TICKET, safeStart);
        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            sendText(to, body.toString());
        }

        if (whatsappProperties.isInteractiveUxEnabled()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = safeStart; i < end; i++) {
                TroubleTicketSummary ticket = tickets.get(i);
                String status = (ticket.status() == null || ticket.status().isBlank())
                        ? translate(to, "UnknownValue")
                        : ticket.status().strip();
                rows.add(buildListRow(String.valueOf(i + 1), format(to, "TicketButtonLabel", ticket.id(), status)));
            }
            if (end < tickets.size()) {
                rows.add(buildListRow(String.valueOf(end + 1), "More tickets"));
            }
            sendInteractiveList(to,
                    translate(to, "SelectTicketPrompt"),
                    translate(to, "WhatsappTicketSelectionInstruction"),
                    rows);
        }
    }

    public List<BusinessMenuItem> currentMenuItems(String userId) {
        String menuId = resolveCurrentMenuId(userId);
        return new ArrayList<>(menuConfigurationProvider.getMenuItems(menuId));
    }

    public List<BusinessMenuItem> currentLoginMenuItems(String userId) {
        String menuId = resolveCurrentLoginMenu(userId);
        return new ArrayList<>(menuConfigurationProvider.getLoginMenuItems(menuId));
    }

    public int currentMenuDepth(String userId) {
        return sessionService.getBusinessMenuDepth(userId, menuConfigurationProvider.getRootMenuId());
    }

    public boolean goToBusinessMenu(String userId, String menuId) {
        if (menuId == null || menuId.isBlank() || !menuConfigurationProvider.menuExists(menuId)) {
            return false;
        }
        sessionService.enterBusinessMenu(userId, menuId, menuConfigurationProvider.getRootMenuId());
        return true;
    }

    public boolean goToLoginMenu(String userId, String menuId) {
        if (menuId == null || menuId.isBlank() || !menuConfigurationProvider.loginMenuExists(menuId)) {
            return false;
        }
        sessionService.enterLoginMenu(userId, menuId, menuConfigurationProvider.getLoginRootMenuId());
        return true;
    }

    public void goHomeBusinessMenu(String userId) {
        sessionService.resetBusinessMenu(userId, menuConfigurationProvider.getRootMenuId());
    }

    public void goHomeLoginMenu(String userId) {
        sessionService.resetLoginMenu(userId, menuConfigurationProvider.getLoginRootMenuId());
    }

    public boolean goUpBusinessMenu(String userId) {
        return sessionService.goUpBusinessMenu(userId, menuConfigurationProvider.getRootMenuId());
    }

    public boolean goUpLoginMenu(String userId) {
        return sessionService.goUpLoginMenu(userId, menuConfigurationProvider.getLoginRootMenuId());
    }

    private String resolveCurrentMenuId(String userId) {
        String menuId = sessionService.currentBusinessMenu(userId, menuConfigurationProvider.getRootMenuId());
        if (!menuConfigurationProvider.menuExists(menuId)) {
            log.warn("User {} had stale menu id {}, resetting to root", userId, menuId);
            sessionService.resetBusinessMenu(userId, menuConfigurationProvider.getRootMenuId());
            menuId = menuConfigurationProvider.getRootMenuId();
        }
        return menuId;
    }

    private String resolveCurrentLoginMenu(String userId) {
        String menuId = sessionService.currentLoginMenu(userId, menuConfigurationProvider.getLoginRootMenuId());
        if (!menuConfigurationProvider.loginMenuExists(menuId)) {
            sessionService.resetLoginMenu(userId, menuConfigurationProvider.getLoginRootMenuId());
            menuId = menuConfigurationProvider.getLoginRootMenuId();
        }
        return menuId;
    }

    private String resolveMenuLabel(String userId, BusinessMenuItem item) {
        String translationKey = item.translationKey();
        if (translationKey != null
                && !translationKey.isBlank()
                && translationService.hasTranslation(language(userId), translationKey)) {
            return translate(userId, translationKey);
        }
        if (item.label() != null && !item.label().isBlank()) {
            return item.label();
        }
        if (item.isWeblink() && item.weblink() != null && !item.weblink().isBlank()) {
            return item.weblink();
        }
        return item.function();
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

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return value.strip();
    }

    private String buildPagedPrompt(String userId, String promptKey, int start, int end, int total) {
        String prompt = translate(userId, promptKey);
        if (total > 5) {
            prompt += " " + format(userId, "ListPageCounter", start + 1, end, total);
        }
        return prompt;
    }

    private boolean postToWhatsapp(Map<String, Object> payload) {
        String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("WhatsApp API responded with status {}", response.getStatusCode());
            return true;
        } catch (HttpStatusCodeException ex) {
            log.error("WhatsApp API error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("Failed to call WhatsApp API", ex);
        }

        return false;
    }

    private boolean isConfigured() {
        return !phoneNumberId.isBlank() && !accessToken.isBlank();
    }

    private boolean shouldSendFallbackText() {
        return whatsappProperties.getUxMode() == WhatsappProperties.WhatsappUxMode.TEST;
    }

    private String resolveWeblinkUrl(String userId, BusinessMenuItem item) {
        if (item == null || !StringUtils.hasText(item.url())) {
            return null;
        }

        String contextualUrl = applyContextualPath(userId, item);
        if (!item.isAuthenticatedLink()) {
            return contextualUrl;
        }
        String exchangeId = sessionService.getExchangeId(userId);
        if (!StringUtils.hasText(exchangeId)) {
            return contextualUrl;
        }
        try {
            return UriComponentsBuilder.fromUriString(contextualUrl)
                    .queryParam("exchangeId", exchangeId)
                    .build(true)
                    .toUriString();
        } catch (Exception ex) {
            log.warn("Failed to append exchangeId to WhatsApp weblink {}: {}", contextualUrl, ex.getMessage());
            String encoded = UriUtils.encode(exchangeId, StandardCharsets.UTF_8);
            return contextualUrl + (contextualUrl.contains("?") ? "&" : "?") + "exchangeId=" + encoded;
        }
    }

    private String applyContextualPath(String userId, BusinessMenuItem item) {
        if (item.requiresAccountContext()) {
            AccountSummary account = sessionService.getSelectedAccount(userId);
            if (account == null || !StringUtils.hasText(account.accountId())) {
                log.warn("No selected account found for contextual WhatsApp weblink {}", item.url());
                return item.url();
            }
            try {
                return UriComponentsBuilder.fromUriString(item.url())
                        .pathSegment(account.accountId())
                        .build(true)
                        .toUriString();
            } catch (Exception ex) {
                log.warn("Failed to append account {} to WhatsApp weblink {}: {}", account.accountId(), item.url(), ex.getMessage());
                String encoded = UriUtils.encodePathSegment(account.accountId(), StandardCharsets.UTF_8);
                return item.url().endsWith("/") ? item.url() + encoded : item.url() + "/" + encoded;
            }
        }

        if (item.requiresServiceContext()) {
            ServiceSummary service = sessionService.getSelectedService(userId);
            if (service == null || !StringUtils.hasText(service.productId())) {
                log.warn("No selected service found for contextual WhatsApp weblink {}", item.url());
                return item.url();
            }
            try {
                return UriComponentsBuilder.fromUriString(item.url())
                        .pathSegment(service.productId())
                        .build(true)
                        .toUriString();
            } catch (Exception ex) {
                log.warn("Failed to append service {} to WhatsApp weblink {}: {}", service.productId(), item.url(), ex.getMessage());
                String encoded = UriUtils.encodePathSegment(service.productId(), StandardCharsets.UTF_8);
                return item.url().endsWith("/") ? item.url() + encoded : item.url() + "/" + encoded;
            }
        }

        return item.url();
    }

    private Map<String, Object> buildListRow(String id, String title) {
        return Map.of(
                "id", id,
                "title", title
        );
    }

    private boolean sendInteractiveList(String to, String title, String instruction, List<Map<String, Object>> rows) {
        if (!isConfigured()) {
            log.warn("WhatsApp messaging is not fully configured; cannot send interactive list");
            return false;
        }
        if (rows == null || rows.isEmpty()) {
            return false;
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",
                        "header", Map.of("type", "text", "text", title),
                        "body", Map.of("text", instruction),
                        "action", Map.of(
                                "button", "Select",
                                "sections", List.of(Map.of(
                                        "title", "Options",
                                        "rows", rows
                                ))
                        )
                )
        );

        return postToWhatsapp(payload);
    }

    public enum TelegramKey {
        BUTTON_SELF_SERVICE_LOGIN("ButtonSelfServiceLogin"),
        BUTTON_DIRECT_LOGIN("ButtonDirectLogin"),
        BUTTON_OPT_IN("ButtonOptIn"),
        BUTTON_CHANGE_LANGUAGE("ButtonChangeLanguage"),
        BUTTON_SETTINGS("ButtonSettings"),
        BUTTON_LOGOUT("ButtonLogout"),
        BUTTON_BUSINESS_MENU_HOME("BusinessMenuHome"),
        BUTTON_BUSINESS_MENU_UP("BusinessMenuUp"),
        SELECT_ACCOUNT_PROMPT("SelectAccountPrompt"),
        BUTTON_CHANGE_ACCOUNT("ButtonChangeAccount");

        private final String key;

        TelegramKey(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return key;
        }
    }
}
