package com.selfservice.whatsapp.service;

import com.selfservice.application.config.LoginMenuProperties;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.ServiceSummary;
import com.selfservice.application.dto.TroubleTicketSummary;
import com.selfservice.telegrambot.config.menu.BusinessMenuConfigurationProvider;
import com.selfservice.telegrambot.config.menu.BusinessMenuItem;
import com.selfservice.telegrambot.service.TranslationService;
import com.selfservice.whatsapp.config.WhatsappProperties;
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

@Service
public class WhatsappService {

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

    public enum LoginMenuOption {
        DIGITAL_LOGIN,
        CRM_LOGIN,
        OPT_IN,
        CHANGE_LANGUAGE
    }

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

    public List<LoginMenuOption> loginMenuOptions() {
        List<LoginMenuOption> options = new ArrayList<>();
        if (loginMenuProperties.isDigitalLoginEnabled()) {
            options.add(LoginMenuOption.DIGITAL_LOGIN);
        }
        if (loginMenuProperties.isCrmLoginEnabled()) {
            options.add(LoginMenuOption.CRM_LOGIN);
        }
        options.add(LoginMenuOption.OPT_IN);
        options.add(LoginMenuOption.CHANGE_LANGUAGE);
        return options;
    }

    public void sendLoginMenu(String to) {
        sessionService.setSelectionContext(to, WhatsappSessionService.SelectionContext.NONE);
        List<LoginMenuOption> options = loginMenuOptions();

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

    private void sendLoginMenuText(String to, List<LoginMenuOption> options) {
        StringBuilder menu = new StringBuilder();
        int index = 1;
        for (LoginMenuOption option : options) {
            if (option == LoginMenuOption.DIGITAL_LOGIN) {
                menu.append(index).append(") ")
                        .append(translate(to, TelegramKey.BUTTON_SELF_SERVICE_LOGIN.toString()))
                        .append("\n");
            } else if (option == LoginMenuOption.CRM_LOGIN) {
                menu.append(index).append(") ")
                        .append(translate(to, TelegramKey.BUTTON_DIRECT_LOGIN.toString()))
                        .append("\n");
            } else if (option == LoginMenuOption.OPT_IN) {
                menu.append(index).append(") ")
                        .append(translate(to, TelegramKey.BUTTON_OPT_IN.toString()))
                        .append("\n");
            } else {
                menu.append(index).append(") ")
                        .append(translate(to, TelegramKey.BUTTON_CHANGE_LANGUAGE.toString()))
                        .append("\n");
            }
            index++;
        }
        menu.append("\n").append(translate(to, "PleaseChooseSignIn"));
        sendText(to, menu.toString());
    }

    private boolean sendLoginMenuCards(String to, List<LoginMenuOption> options) {
        if (!isConfigured()) {
            log.warn("WhatsApp messaging is not fully configured; cannot send login menu cards");
            return false;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (LoginMenuOption option : options) {
            if (option == LoginMenuOption.DIGITAL_LOGIN) {
                rows.add(buildListRow(INTERACTIVE_ID_DIGITAL_LOGIN,
                        translate(to, TelegramKey.BUTTON_SELF_SERVICE_LOGIN.toString())));
            } else if (option == LoginMenuOption.CRM_LOGIN) {
                rows.add(buildListRow(INTERACTIVE_ID_CRM_LOGIN,
                        translate(to, TelegramKey.BUTTON_DIRECT_LOGIN.toString())));
            } else if (option == LoginMenuOption.OPT_IN) {
                rows.add(buildListRow(INTERACTIVE_ID_OPT_IN,
                        translate(to, TelegramKey.BUTTON_OPT_IN.toString())));
            } else {
                rows.add(buildListRow(INTERACTIVE_ID_CHANGE_LANGUAGE,
                        translate(to, TelegramKey.BUTTON_CHANGE_LANGUAGE.toString())));
            }
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
        String yes = translate(to, TelegramService.KEY_OPT_IN_YES);
        String no = translate(to, TelegramService.KEY_OPT_IN_NO);
        sendText(to, String.format("%s\n\n- %s\n- %s", prompt, yes, no));
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
        for (BusinessMenuItem item : menuItems) {
            if (item.isSubMenu() && !menuConfigurationProvider.menuExists(item.submenuId())) {
                log.warn("User {} attempted to render missing submenu {}", to, item.submenuId());
                continue;
            }
            body.append(index).append(") ")
                    .append(resolveMenuLabel(to, item))
                    .append("\n");
            index++;
        }

        int depth = sessionService.getBusinessMenuDepth(to, menuConfigurationProvider.getRootMenuId());
        int actionIndex = index;
        if (depth >= 1) {
            body.append(actionIndex).append(") ")
                    .append(translate(to, TelegramKey.BUTTON_BUSINESS_MENU_HOME.toString()))
                    .append("\n");
            actionIndex++;
            if (depth >= 2) {
                body.append(actionIndex).append(") ")
                        .append(translate(to, TelegramKey.BUTTON_BUSINESS_MENU_UP.toString()))
                        .append("\n");
                actionIndex++;
            }
        }
        if (showChangeAccountOption) {
            body.append(actionIndex).append(") ")
                    .append(translate(to, TelegramKey.BUTTON_CHANGE_ACCOUNT.toString()))
                    .append("\n");
            actionIndex++;
        }
        body.append(actionIndex).append(") ")
                .append(translate(to, TelegramKey.BUTTON_LOGOUT.toString()))
                .append("\n");
        body.append(translate(to, "WhatsappMenuInstruction"));
        if (whatsappProperties.isBasicUxEnabled() || shouldSendFallbackText()) {
            sendText(to, body.toString());
        }

        if (whatsappProperties.isInteractiveUxEnabled()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            int rowIndex = 1;
            for (BusinessMenuItem item : menuItems) {
                if (item.isSubMenu() && !menuConfigurationProvider.menuExists(item.submenuId())) {
                    continue;
                }
                rows.add(buildListRow(String.valueOf(rowIndex), resolveMenuLabel(to, item)));
                rowIndex++;
            }

            int depthLevel = sessionService.getBusinessMenuDepth(to, menuConfigurationProvider.getRootMenuId());
            if (depthLevel >= 1) {
                rows.add(buildListRow(String.valueOf(rowIndex),
                        translate(to, TelegramKey.BUTTON_BUSINESS_MENU_HOME.toString())));
                rowIndex++;
                if (depthLevel >= 2) {
                    rows.add(buildListRow(String.valueOf(rowIndex),
                            translate(to, TelegramKey.BUTTON_BUSINESS_MENU_UP.toString())));
                    rowIndex++;
                }
            }
            if (showChangeAccountOption) {
                rows.add(buildListRow(String.valueOf(rowIndex),
                        translate(to, TelegramKey.BUTTON_CHANGE_ACCOUNT.toString())));
                rowIndex++;
            }
            rows.add(buildListRow(String.valueOf(rowIndex), translate(to, TelegramKey.BUTTON_LOGOUT.toString())));

            sendInteractiveList(to,
                    translate(to, "LoginWelcome"),
                    translate(to, "WhatsappMenuInstruction"),
                    rows);
        }
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

    public void goHomeBusinessMenu(String userId) {
        sessionService.resetBusinessMenu(userId, menuConfigurationProvider.getRootMenuId());
    }

    public boolean goUpBusinessMenu(String userId) {
        return sessionService.goUpBusinessMenu(userId, menuConfigurationProvider.getRootMenuId());
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
