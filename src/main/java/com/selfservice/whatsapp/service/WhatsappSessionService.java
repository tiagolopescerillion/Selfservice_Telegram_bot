package com.selfservice.whatsapp.service;

import com.selfservice.application.dto.AccountSummary;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WhatsappSessionService {

    public static final class TokenInfo {
        public final String accessToken;
        public final String refreshToken;
        public final String idToken;
        public final long expiryEpochMs;
        public final List<AccountSummary> accounts;

        public TokenInfo(String accessToken, String refreshToken, String idToken, long expiryEpochMs,
                List<AccountSummary> accounts) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.idToken = idToken;
            this.expiryEpochMs = expiryEpochMs;
            this.accounts = accounts;
        }
    }

    private final Map<String, TokenInfo> byUser = new ConcurrentHashMap<>();

    public void save(String userId, String accessToken, String refreshToken, String idToken, long expiresInSeconds) {
        long exp = System.currentTimeMillis() + expiresInSeconds * 1000L;
        byUser.put(userId, new TokenInfo(accessToken, refreshToken, idToken, exp, Collections.emptyList()));
    }

    public String getValidAccessToken(String userId) {
        TokenInfo info = byUser.get(userId);
        if (info == null) {
            return null;
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byUser.remove(userId);
            return null;
        }
        return info.accessToken;
    }

    public void saveAccounts(String userId, List<AccountSummary> accounts) {
        final List<AccountSummary> copy = accounts == null ? Collections.emptyList() : List.copyOf(accounts);
        byUser.computeIfPresent(userId, (id, existing) -> {
            if (existing.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
                return null;
            }
            return new TokenInfo(existing.accessToken, existing.refreshToken, existing.idToken, existing.expiryEpochMs, copy);
        });
    }

    public List<AccountSummary> getAccounts(String userId) {
        TokenInfo info = byUser.get(userId);
        if (info == null) {
            return List.of();
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byUser.remove(userId);
            return List.of();
        }
        return info.accounts;
    }

    public String getIdToken(String userId) {
        TokenInfo info = byUser.get(userId);
        if (info == null) {
            return null;
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byUser.remove(userId);
            return null;
        }
        return info.idToken;
    }

    public String getRefreshToken(String userId) {
        TokenInfo info = byUser.get(userId);
        if (info == null) {
            return null;
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byUser.remove(userId);
            return null;
        }
        return info.refreshToken;
    }

    public boolean clear(String userId) {
        return byUser.remove(userId) != null;
    }
}

