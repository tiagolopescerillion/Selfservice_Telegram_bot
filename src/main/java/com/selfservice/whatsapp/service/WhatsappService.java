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


    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);

    public static final String COMMAND_CHANGE_LANGUAGE = "language";
    public static final String COMMAND_LOGOUT = "logout";
    public static final String COMMAND_CHANGE_ACCOUNT = "change account";
    public static final String COMMAND_HOME = "home";
    public static final String COMMAND_UP = "up";
    public static final String INTERACTIVE_ID_DIGITAL_LOGIN = "DIGITAL_LOGIN";
    public static final String INTERACTIVE_ID_CRM_LOGIN = "CRM_LOGIN";
    public static final String INTERACTIVE_ID_CHANGE_LANGUAGE = "CHANGE_LANGUAGE";
    public static final String INTERACTIVE_ID_OPT_IN = "OPT_IN";
    public static final String INTERACTIVE_ID_SETTINGS = "SETTINGS";
    private static final int WHATSAPP_ROW_TITLE_LIMIT = 24;
    private static final int WHATSAPP_HEADER_TEXT_LIMIT = 60;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String phoneNumberId;
    private final String accessToken;
    private final TranslationService translationService;
    private final WhatsappSessionService sessionService;
    private final BusinessMenuConfigurationProvider menuConfigurationProvider;
    private final LoginMenuProperties loginMenuProperties;

    public WhatsappService(
            @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.access-token:}") String accessToken,
            TranslationService translationService,
            WhatsappSessionService sessionService,
            BusinessMenuConfigurationProvider menuConfigurationProvider,
            LoginMenuProperties loginMenuProperties) {
        this.phoneNumberId = phoneNumberId == null ? "" : phoneNumberId.trim();
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.translationService = translationService;
        this.sessionService = sessionService;
        this.menuConfigurationProvider = menuConfigurationProvider;
        this.loginMenuProperties = loginMenuProperties;
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
            rows.add(buildListRow(String.valueOf(index++), label));
        }

        if (rows.isEmpty()) {
            sendText(to, message);
            return;
        }

        String title = (message == null || message.isBlank()) ? "Select an option" : message;
        boolean sent = sendInteractiveList(to, title, title, rows);
        if (!sent) {
            StringBuilder prompt = new StringBuilder(title).append('\n');
            for (int i = 0; i < buttonLabels.size(); i++) {
                prompt.append(i + 1).append(") ").append(buttonLabels.get(i)).append('\n');
            }
            sendText(to, prompt.toString());
        }
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
            String label = item.getLabel().trim();
            if (!looksLikeUrl(label)) {
                return label;
            }
        }
        LoginMenuFunction function = item.resolvedFunction();
        if (function == LoginMenuFunction.DIGITAL_LOGIN) {
            return "Self Service Login";
        }
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
        sendLoginMenuCards(to, options);
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
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.OPT_IN);
        sendInteractiveList(to,
                prompt,
                prompt,
                List.of(
                        buildListRow("1", yes),
                        buildListRow("2", no)
                ));
    }

    public void sendOptInAccepted(String to) {
        sendText(to, translate(to, "OptInAccepted"));
    }

    public void sendOptInDeclined(String to) {
        sendText(to, translate(to, "OptInDeclined"));
    }

    public void sendLanguageMenu(String to) {
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.NONE);
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

    public void sendSettingsMenu(String to) {
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.SETTINGS);
        String settingsMenuId = menuConfigurationProvider.getLoginSettingsMenuId();
        if (settingsMenuId != null) {
            goToLoginMenu(to, settingsMenuId);
        }
        List<LoginMenuItem> options = loginSettingsMenuOptions(to);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LoginMenuItem option : options) {
            rows.add(buildListRow(callbackId(option), resolveLoginMenuLabel(to, option)));
        }
        sendInteractiveList(to,
                translate(to, "SettingsMenuPrompt"),
                translate(to, "SettingsMenuPrompt"),
                rows);
    }

    public void sendLoggedInMenu(String to, AccountSummary selectedAccount, boolean showChangeAccountOption) {
        sendLoggedInMenu(to, selectedAccount, showChangeAccountOption, null);
    }

    public void sendLoggedInMenu(String to, AccountSummary selectedAccount, boolean showChangeAccountOption, String greeting) {
        String menuId = resolveCurrentMenuId(to);
        List<BusinessMenuItem> menuItems = menuConfigurationProvider.getMenuItems(menuId);
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.NONE);

        boolean hasGreeting = greeting != null && !greeting.isBlank();
        String storedContext = sessionService.getMenuContext(to);

        String combinedContext = hasGreeting ? greeting : storedContext;
        boolean contextContainsChoice = combinedContext != null && combinedContext.contains("Choose an option");
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
        }

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

        String header = combinedContext != null && !combinedContext.isBlank()
                ? combinedContext
                : translate(to, "LoginWelcome");
        if (!contextContainsChoice && header.equals(combinedContext)) {
            header = header + "\n" + translate(to, "LoginWelcome");
        }
        sendInteractiveList(to,
                header,
                translate(to, "WhatsappMenuInstruction"),
                rows);
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

    private boolean shouldDisplayBusinessMenuItem(BusinessMenuItem item, int menuDepth, boolean hasAlternateAccount, boolean loggedIn) {
        if (isLogoutItem(item) && !loggedIn) {
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

        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.ACCOUNT, safeStart);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = safeStart; i < end; i++) {
            AccountSummary summary = accounts.get(i);
            rows.add(buildListRow(String.valueOf(i + 1), summary.displayLabel()));
        }
        if (end < accounts.size()) {
            rows.add(buildListRow("0", "More accounts"));
        }
        String title = buildPagedPrompt(to, TelegramKey.SELECT_ACCOUNT_PROMPT.toString(), safeStart, end, accounts.size());
        String instruction = translate(to, "WhatsappAccountSelectionInstruction");
        String combinedTitle = (header == null || header.isBlank()) ? title : header + "\n" + title;
        sendInteractiveList(to, combinedTitle, instruction, rows);
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

        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.SERVICE, safeStart);
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
            rows.add(buildListRow("0", "More services"));
        }
        sendInteractiveList(to,
                buildPagedPrompt(to, "SelectServicePrompt", safeStart, end, services.size()),
                translate(to, "WhatsappServiceSelectionInstruction"),
                rows);
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

        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.INVOICE, safeStart);
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
            rows.add(buildListRow("0", "More invoices"));
        }
        sendInteractiveList(to,
                buildPagedPrompt(to, "SelectInvoicePrompt", safeStart, end, invoices.size()),
                translate(to, "WhatsappInvoiceSelectionInstruction"),
                rows);
    }

    public void sendInvoiceActions(String to, InvoiceSummary invoice) {
        if (invoice == null || invoice.id() == null || invoice.id().isBlank()) {
            sendText(to, translate(to, "InvoiceNoLongerAvailable"));
            return;
        }
        List<BusinessMenuItem> actions = invoiceActions(to);
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.INVOICE_ACTION);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            rows.add(buildListRow(String.valueOf(i + 1), resolveMenuLabel(to, actions.get(i))));
        }
        sendInteractiveList(to,
                format(to, "InvoiceActionsPrompt", invoice.id()),
                translate(to, "InvoiceActionsInstruction"),
                rows);
    }

    public List<BusinessMenuItem> invoiceActions(String userId) {
        List<BusinessMenuItem> actions = menuConfigurationProvider
                .getMenuItems(sessionService.getInvoiceActionsMenu(userId));
        if (actions.isEmpty()) {
            return List.of(
                    fallbackAction(1, translate(userId, "ButtonInvoiceViewPdf"),
                            TelegramService.CALLBACK_INVOICE_VIEW_PDF_PREFIX),
                    fallbackAction(2, translate(userId, "ButtonInvoicePay"),
                            TelegramService.CALLBACK_INVOICE_PAY_PREFIX),
                    fallbackAction(3, translate(userId, "ButtonInvoiceCompare"),
                            TelegramService.CALLBACK_INVOICE_COMPARE_PREFIX));
        }
        return actions;
    }

    private BusinessMenuItem fallbackAction(int order, String label, String callback) {
        return new BusinessMenuItem(
                "function",
                order,
                label,
                callback,
                callback,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
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

        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.TICKET, safeStart);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = safeStart; i < end; i++) {
            TroubleTicketSummary ticket = tickets.get(i);
            String status = (ticket.status() == null || ticket.status().isBlank())
                    ? translate(to, "UnknownValue")
                    : ticket.status().strip();
            rows.add(buildListRow(String.valueOf(i + 1), format(to, "TicketButtonLabel", ticket.id(), status)));
        }
        if (end < tickets.size()) {
            rows.add(buildListRow("0", "More tickets"));
        }
        sendInteractiveList(to,
                buildPagedPrompt(to, "SelectTicketPrompt", safeStart, end, tickets.size()),
                translate(to, "WhatsappTicketSelectionInstruction"),
                rows);
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
        sessionService.clearMenuContext(userId);
        sessionService.clearPendingFunctionMenu(userId);
    }

    public void goHomeLoginMenu(String userId) {
        sessionService.resetLoginMenu(userId, menuConfigurationProvider.getLoginRootMenuId());
        sessionService.setSelectionContext(userId, WhatsappSessionService.SelectionContext.NONE);
        sessionService.setAwaitingLanguageSelection(userId, false);
        sessionService.clearMenuContext(userId);
        sessionService.clearPendingFunctionMenu(userId);
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
            String label = item.label().trim();
            if (!looksLikeUrl(label)) {
                return label;
            }
        }
        if (item.isWeblink() && item.weblink() != null && !item.weblink().isBlank()) {
            String link = item.weblink().trim();
            if (!looksLikeUrl(link)) {
                return link;
            }
            return "Open link";
        }
        return item.function();
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
                "title", normalizeRowTitle(title)
        );
    }

    private String normalizeRowTitle(String title) {
        String value = safeText(title, "Option");
        if (value.length() <= WHATSAPP_ROW_TITLE_LIMIT) {
            return value;
        }
        return value.substring(0, WHATSAPP_ROW_TITLE_LIMIT);
    }

    private String normalizeHeaderText(String title) {
        String value = safeText(title, "");
        if (value.length() <= WHATSAPP_HEADER_TEXT_LIMIT) {
            return value;
        }
        return value.substring(0, WHATSAPP_HEADER_TEXT_LIMIT);
    }

    private boolean looksLikeUrl(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    private boolean sendInteractiveList(String to, String title, String instruction, List<Map<String, Object>> rows) {
        if (!isConfigured()) {
            log.warn("WhatsApp messaging is not fully configured; cannot send interactive list");
            return false;
        }
        if (rows == null || rows.isEmpty()) {
            return false;
        }

        String headerText = normalizeHeaderText(title);
        String instructionText = safeText(instruction, "");
        if (!headerText.isBlank()
                && !instructionText.isBlank()
                && headerText.strip().equals(instructionText.strip())) {
            instructionText = " ";
        }
        Map<String, Object> interactive = new java.util.HashMap<>();
        interactive.put("type", "list");
        if (!headerText.isBlank()) {
            interactive.put("header", Map.of("type", "text", "text", headerText));
        }
        interactive.put("body", Map.of("text", instructionText));
        interactive.put("action", Map.of(
                "button", "Select",
                "sections", List.of(Map.of(
                        "title", "Options",
                        "rows", rows
                ))
        ));

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "interactive",
                "interactive", interactive
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
