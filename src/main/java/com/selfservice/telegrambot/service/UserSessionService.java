package com.selfservice.telegrambot.service;

import com.selfservice.telegrambot.service.dto.AccountSummary;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {

    public static final class TokenInfo {
        public final String accessToken;
        public final long expiryEpochMs;
        public final List<AccountSummary> accounts;

        public TokenInfo(String accessToken, long expiryEpochMs, List<AccountSummary> accounts) {
            this.accessToken = accessToken;
            this.expiryEpochMs = expiryEpochMs;
            this.accounts = accounts;
        }
    }

    private final Map<Long, TokenInfo> byChat = new ConcurrentHashMap<>();

    public void save(long chatId, String accessToken, long expiresInSeconds) {
        long exp = System.currentTimeMillis() + expiresInSeconds * 1000L;
        byChat.put(chatId, new TokenInfo(accessToken, exp, Collections.emptyList()));
    }

    public String getValidAccessToken(long chatId) {
        TokenInfo ti = byChat.get(chatId);
        if (ti == null) return null;
        // 30s safety buffer
        if (ti.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byChat.remove(chatId);
            return null;
        }
        return ti.accessToken;
    }

    public void saveAccounts(long chatId, List<AccountSummary> accounts) {
        List<AccountSummary> copy = accounts == null ? Collections.emptyList() : List.copyOf(accounts);
        byChat.computeIfPresent(chatId, (id, existing) -> {
            if (existing.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
                return null;
            }
            return new TokenInfo(existing.accessToken, existing.expiryEpochMs, copy);
        });
    }

    public List<AccountSummary> getAccounts(long chatId) {
        TokenInfo info = byChat.get(chatId);
        if (info == null) {
            return List.of();
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byChat.remove(chatId);
            return List.of();
        }
        return info.accounts;
    }
}
