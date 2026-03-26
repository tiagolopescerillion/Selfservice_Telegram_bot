package com.selfservice.whatsapp.controller;

import com.selfservice.application.auth.KeycloakAuthService;
import com.selfservice.application.auth.OAuthSessionService;
import com.selfservice.application.config.ConnectorsProperties;
import com.selfservice.application.config.menu.BusinessMenuConfigurationProvider;
import com.selfservice.application.config.menu.LoginMenuItem;
import com.selfservice.application.service.AccountBalanceService;
import com.selfservice.application.service.ContextTraceLogger;
import com.selfservice.application.service.InvoiceService;
import com.selfservice.application.service.OperationsMonitoringService;
import com.selfservice.application.service.ProductService;
import com.selfservice.application.service.ServiceFunctionExecutor;
import com.selfservice.application.service.TroubleTicketService;
import com.selfservice.whatsapp.service.WhatsappService;
import com.selfservice.whatsapp.service.WhatsappSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsappWebhookControllerTest {

    @Mock
    private WhatsappService whatsappService;
    @Mock
    private OAuthSessionService oauthSessionService;
    @Mock
    private KeycloakAuthService keycloakAuthService;
    @Mock
    private ProductService productService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private TroubleTicketService troubleTicketService;
    @Mock
    private BusinessMenuConfigurationProvider menuConfigurationProvider;
    @Mock
    private ServiceFunctionExecutor serviceFunctionExecutor;
    @Mock
    private ContextTraceLogger contextTraceLogger;
    @Mock
    private AccountBalanceService accountBalanceService;

    private WhatsappSessionService sessionService;
    private OperationsMonitoringService monitoringService;
    private ConnectorsProperties connectorsProperties;
    private WhatsappWebhookController controller;

    @BeforeEach
    void setUp() {
        sessionService = new WhatsappSessionService();
        monitoringService = new OperationsMonitoringService();
        connectorsProperties = new ConnectorsProperties();
        connectorsProperties.setWhatsapp(true);
        controller = new WhatsappWebhookController(
                whatsappService,
                oauthSessionService,
                sessionService,
                keycloakAuthService,
                productService,
                invoiceService,
                troubleTicketService,
                "verify-token",
                monitoringService,
                connectorsProperties,
                menuConfigurationProvider,
                serviceFunctionExecutor,
                contextTraceLogger,
                accountBalanceService);

        when(whatsappService.translate(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1, String.class));
        when(whatsappService.loginMenuOptions(anyString())).thenReturn(List.of());
        when(whatsappService.loginSettingsMenuOptions(anyString())).thenReturn(List.of());
    }

    @Test
    void onEventDispatchesReplyButtonDigitalLoginSelections() {
        String from = "447700900001";
        String loginUrl = "https://login.example";
        when(oauthSessionService.buildAuthUrl("WhatsApp", "wa-" + from)).thenReturn(loginUrl);

        var response = controller.onEvent(interactiveReplyPayload(from, WhatsappService.INTERACTIVE_ID_DIGITAL_LOGIN,
                "Self Service Login"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(whatsappService).sendDigitalLoginLink(from, loginUrl);
        verify(whatsappService, never()).sendLoginMenu(from);
    }

    @Test
    void onEventDispatchesReplyButtonCustomLoginWeblinks() {
        String from = "447700900002";
        LoginMenuItem eshop = new LoginMenuItem(
                "action",
                1,
                "eShop",
                null,
                "ESHOP",
                null,
                null,
                null,
                "https://eshop.example",
                false,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                null,
                false,
                null,
                null,
                false,
                null,
                null);
        when(whatsappService.loginMenuOptions(from)).thenReturn(List.of(eshop));
        when(whatsappService.callbackId(eshop)).thenReturn("ESHOP");

        var response = controller.onEvent(interactiveReplyPayload(from, "ESHOP", "eShop"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(whatsappService).sendLoginWeblink(from, eshop);
        verify(whatsappService).sendLoginMenu(from);
    }

    private Map<String, Object> interactiveReplyPayload(String from, String id, String title) {
        return Map.of(
                "entry", List.of(Map.of(
                        "changes", List.of(Map.of(
                                "value", Map.of(
                                        "messages", List.of(Map.of(
                                                "from", from,
                                                "type", "interactive",
                                                "interactive", Map.of(
                                                        "button_reply", Map.of(
                                                                "id", id,
                                                                "title", title
                                                        )
                                                )
                                        ))
                                )
                        ))
                ))
        );
    }
}
