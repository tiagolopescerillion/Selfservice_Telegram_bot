package com.selfservice.telegrambot.service;

import com.selfservice.telegrambot.service.dto.AccountSummary;
import com.selfservice.telegrambot.service.dto.ServiceSummary;
import com.selfservice.telegrambot.service.dto.TroubleTicketSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserSessionServiceTest {

    @Test
    void clearSessionRemovesAllStoredState() {
        UserSessionService service = new UserSessionService();
        long chatId = 42L;

        service.save(chatId, "token", "refresh", 3_600);
        service.saveAccounts(chatId, List.of(new AccountSummary("acct-1", "Account")));
        service.selectAccount(chatId, new AccountSummary("acct-1", "Account"));
        service.saveServices(chatId, List.of(new ServiceSummary("svc-1", "Service", "123")));
        service.saveTroubleTickets(chatId, List.of(new TroubleTicketSummary("tt-1", "OPEN", "desc")));
        service.setLanguage(chatId, "es");

        assertThat(service.getValidAccessToken(chatId)).isEqualTo("token");
        assertThat(service.getAccounts(chatId)).hasSize(1);
        assertThat(service.getSelectedAccount(chatId)).isNotNull();
        assertThat(service.getServices(chatId)).hasSize(1);
        assertThat(service.getTroubleTickets(chatId)).hasSize(1);
        assertThat(service.getLanguage(chatId)).isEqualTo("es");
        assertThat(service.getRefreshToken(chatId)).isEqualTo("refresh");

        service.clearSession(chatId);

        assertThat(service.getValidAccessToken(chatId)).isNull();
        assertThat(service.getAccounts(chatId)).isEmpty();
        assertThat(service.getSelectedAccount(chatId)).isNull();
        assertThat(service.getServices(chatId)).isEmpty();
        assertThat(service.getTroubleTickets(chatId)).isEmpty();
        assertThat(service.getLanguage(chatId)).isEqualTo("en");
        assertThat(service.getRefreshToken(chatId)).isNull();
    }
}
