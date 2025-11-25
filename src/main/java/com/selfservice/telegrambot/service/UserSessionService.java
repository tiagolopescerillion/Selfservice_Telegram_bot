package com.selfservice.telegrambot.service;

import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.ServiceSummary;
import com.selfservice.application.dto.TroubleTicketSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {

    public static final class TokenInfo {
        public final String accessToken;
        public final String refreshToken;
        public final String idToken;
        public final long expiryEpochMs;
        public final List<AccountSummary> accounts;
        public final AccountSummary selectedAccount;

        public TokenInfo(String accessToken, String refreshToken, String idToken, long expiryEpochMs,
                List<AccountSummary> accounts, AccountSummary selectedAccount) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.idToken = idToken;
            this.expiryEpochMs = expiryEpochMs;
            this.accounts = accounts;
            this.selectedAccount = selectedAccount;
        }
    }

    public enum TokenState {
        NONE,
        VALID,
        EXPIRED,
        INVALID
    }

    public record TokenSnapshot(TokenState state, String token) {
        public static TokenSnapshot none() {
            return new TokenSnapshot(TokenState.NONE, null);
        }
    }

    private static final String DEFAULT_LANGUAGE = "en";

    private final Map<Long, TokenInfo> byChat = new ConcurrentHashMap<>();
    private final Map<Long, List<ServiceSummary>> servicesByChat = new ConcurrentHashMap<>();
    private final Map<Long, List<TroubleTicketSummary>> ticketsByChat = new ConcurrentHashMap<>();
    private final Map<Long, String> languageByChat = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> menuPathByChat = new ConcurrentHashMap<>();

    public void save(long chatId, String accessToken, String refreshToken, String idToken, long expiresInSeconds) {
        long exp = System.currentTimeMillis() + expiresInSeconds * 1000L;
        byChat.put(chatId, new TokenInfo(accessToken, refreshToken, idToken, exp, Collections.emptyList(), null));
        clearServices(chatId);
        clearTroubleTickets(chatId);
    }

    public TokenSnapshot getTokenSnapshot(long chatId) {
        TokenInfo info = byChat.get(chatId);
        if (info == null) {
            return TokenSnapshot.none();
        }
        boolean expired = info.expiryEpochMs <= System.currentTimeMillis() + 30_000;
        return new TokenSnapshot(expired ? TokenState.EXPIRED : TokenState.VALID, info.accessToken);
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
            AccountSummary selected = null;
            AccountSummary priorSelection = existing.selectedAccount;
            if (priorSelection != null) {
                String selectedAccountId = priorSelection.accountId();
                selected = copy.stream()
                        .filter(a -> a.accountId().equals(selectedAccountId))
                        .findFirst()
                        .orElse(null);
            }
            return new TokenInfo(existing.accessToken, existing.refreshToken, existing.idToken, existing.expiryEpochMs, copy, selected);
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
            return new TokenInfo(existing.accessToken, existing.refreshToken, existing.idToken, existing.expiryEpochMs, existing.accounts,
                    matched);
        });
        clearServices(chatId);
        clearTroubleTickets(chatId);
    }

    public void clearSelectedAccount(long chatId) {
        byChat.computeIfPresent(chatId, (id, existing) -> {
            if (existing.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
                return null;
            }
            if (existing.selectedAccount == null) {
                return existing;
            }
            return new TokenInfo(existing.accessToken, existing.refreshToken, existing.idToken, existing.expiryEpochMs, existing.accounts,
                    null);
        });
        clearServices(chatId);
        clearTroubleTickets(chatId);
    }

    public void saveServices(long chatId, List<ServiceSummary> services) {
        servicesByChat.put(chatId, services == null ? List.of() : List.copyOf(services));
    }

    public List<ServiceSummary> getServices(long chatId) {
        return servicesByChat.getOrDefault(chatId, List.of());
    }

    public void clearServices(long chatId) {
        servicesByChat.remove(chatId);
    }

    public void saveTroubleTickets(long chatId, List<TroubleTicketSummary> tickets) {
        ticketsByChat.put(chatId, tickets == null ? List.of() : List.copyOf(tickets));
    }

    public List<TroubleTicketSummary> getTroubleTickets(long chatId) {
        return ticketsByChat.getOrDefault(chatId, List.of());
    }

    public void clearTroubleTickets(long chatId) {
        ticketsByChat.remove(chatId);
    }

    public void clearSession(long chatId) {
        byChat.remove(chatId);
        servicesByChat.remove(chatId);
        ticketsByChat.remove(chatId);
        languageByChat.remove(chatId);
        menuPathByChat.remove(chatId);
    }

    public String getRefreshToken(long chatId) {
        TokenInfo info = byChat.get(chatId);
        if (info == null) {
            return null;
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byChat.remove(chatId);
            return null;
        }
        return info.refreshToken;
    }

    public String getIdToken(long chatId) {
        TokenInfo info = byChat.get(chatId);
        if (info == null) {
            return null;
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byChat.remove(chatId);
            return null;
        }
        return info.idToken;
    }

    public String getLanguage(long chatId) {
        return languageByChat.getOrDefault(chatId, DEFAULT_LANGUAGE);
    }

    public void setLanguage(long chatId, String language) {
        if (language == null || language.isBlank()) {
            languageByChat.remove(chatId);
            return;
        }
        languageByChat.put(chatId, language);
    }

    public void resetBusinessMenu(long chatId, String rootMenuId) {
        menuPathByChat.put(chatId, new ArrayList<>(List.of(rootMenuId)));
    }

    public String currentBusinessMenu(long chatId, String rootMenuId) {
        List<String> path = ensureMenuPath(chatId, rootMenuId);
        return path.get(path.size() - 1);
    }

    public void enterBusinessMenu(long chatId, String menuId, String rootMenuId) {
        menuPathByChat.compute(chatId, (id, existing) -> {
            List<String> path = (existing == null || existing.isEmpty())
                    ? new ArrayList<>(List.of(rootMenuId))
                    : new ArrayList<>(existing);
            path.add(menuId);
            return path;
        });
    }

    public boolean goUpBusinessMenu(long chatId, String rootMenuId) {
        final boolean[] moved = {false};
        menuPathByChat.compute(chatId, (id, existing) -> {
            List<String> path = (existing == null || existing.isEmpty())
                    ? new ArrayList<>(List.of(rootMenuId))
                    : new ArrayList<>(existing);
            if (path.size() > 1) {
                path.remove(path.size() - 1);
                moved[0] = true;
            }
            return path;
        });
        return moved[0];
    }

    public int getBusinessMenuDepth(long chatId, String rootMenuId) {
        List<String> path = ensureMenuPath(chatId, rootMenuId);
        return Math.max(0, path.size() - 1);
    }

    private List<String> ensureMenuPath(long chatId, String rootMenuId) {
        return menuPathByChat.compute(chatId, (id, existing) -> {
            if (existing == null || existing.isEmpty()) {
                return new ArrayList<>(List.of(rootMenuId));
            }
            return existing;
        });
    }
}
