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
        public final AccountSummary selectedAccount;

        public TokenInfo(String accessToken, long expiryEpochMs, List<AccountSummary> accounts,
                AccountSummary selectedAccount) {
            this.accessToken = accessToken;
            this.expiryEpochMs = expiryEpochMs;
            this.accounts = accounts;
            this.selectedAccount = selectedAccount;
        }
    }

    private final Map<Long, TokenInfo> byChat = new ConcurrentHashMap<>();

    public void save(long chatId, String accessToken, long expiresInSeconds) {
        long exp = System.currentTimeMillis() + expiresInSeconds * 1000L;
        byChat.put(chatId, new TokenInfo(accessToken, exp, Collections.emptyList(), null));
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
        final List<AccountSummary> copy = accounts == null ? Collections.emptyList() : List.copyOf(accounts);
        byChat.computeIfPresent(chatId, (id, existing) -> {
            if (existing.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
                return null;
            }
            AccountSummary selected = existing.selectedAccount;
            if (selected != null) {
                selected = copy.stream()
                        .filter(a -> a.accountId().equals(selected.accountId()))
                        .findFirst()
                        .orElse(null);
            }
            return new TokenInfo(existing.accessToken, existing.expiryEpochMs, copy, selected);
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

    public AccountSummary getSelectedAccount(long chatId) {
        TokenInfo info = byChat.get(chatId);
        if (info == null) {
            return null;
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byChat.remove(chatId);
            return null;
        }
        return info.selectedAccount;
    }

    public void selectAccount(long chatId, AccountSummary account) {
        if (account == null) {
            return;
        }
        byChat.computeIfPresent(chatId, (id, existing) -> {
            if (existing.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
                return null;
            }
            AccountSummary matched = existing.accounts.stream()
                    .filter(a -> a.accountId().equals(account.accountId()))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                return existing;
            }
            return new TokenInfo(existing.accessToken, existing.expiryEpochMs, existing.accounts, matched);
        });
    }

    public void clearSelectedAccount(long chatId) {
        byChat.computeIfPresent(chatId, (id, existing) -> {
            if (existing.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
                return null;
            }
            if (existing.selectedAccount == null) {
                return existing;
            }
            return new TokenInfo(existing.accessToken, existing.expiryEpochMs, existing.accounts, null);
        });
    }
}
