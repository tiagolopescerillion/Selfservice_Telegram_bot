package com.selfservice.whatsapp.service;

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
public class WhatsappSessionService {

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

    private final Map<String, TokenInfo> byUser = new ConcurrentHashMap<>();
    private final Map<String, List<ServiceSummary>> servicesByUser = new ConcurrentHashMap<>();
    private final Map<String, List<TroubleTicketSummary>> ticketsByUser = new ConcurrentHashMap<>();
    private final Map<String, String> languageByUser = new ConcurrentHashMap<>();
    private final Map<String, List<String>> menuPathByUser = new ConcurrentHashMap<>();
    private final Map<String, Boolean> awaitingLanguageSelectionByUser = new ConcurrentHashMap<>();

    public void save(String userId, String accessToken, String refreshToken, String idToken, long expiresInSeconds) {
        long exp = System.currentTimeMillis() + expiresInSeconds * 1000L;
        byUser.put(userId, new TokenInfo(accessToken, refreshToken, idToken, exp, Collections.emptyList(), null));
        clearServices(userId);
        clearTroubleTickets(userId);
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
            AccountSummary selected = null;
            AccountSummary priorSelection = existing.selectedAccount;
            if (priorSelection != null) {
                String selectedAccountId = priorSelection.accountId();
                selected = copy.stream()
                        .filter(a -> a.accountId().equals(selectedAccountId))
                        .findFirst()
                        .orElse(null);
            }
            return new TokenInfo(existing.accessToken, existing.refreshToken, existing.idToken, existing.expiryEpochMs, copy,
                    selected);
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

    public AccountSummary getSelectedAccount(String userId) {
        TokenInfo info = byUser.get(userId);
        if (info == null) {
            return null;
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byUser.remove(userId);
            return null;
        }
        return info.selectedAccount;
    }

    public void selectAccount(String userId, AccountSummary account) {
        if (account == null) {
            return;
        }
        byUser.computeIfPresent(userId, (id, existing) -> {
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
            return new TokenInfo(existing.accessToken, existing.refreshToken, existing.idToken, existing.expiryEpochMs,
                    existing.accounts,
                    matched);
        });
        clearServices(userId);
        clearTroubleTickets(userId);
    }

    public void clearSelectedAccount(String userId) {
        byUser.computeIfPresent(userId, (id, existing) -> {
            if (existing.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
                return null;
            }
            if (existing.selectedAccount == null) {
                return existing;
            }
            return new TokenInfo(existing.accessToken, existing.refreshToken, existing.idToken, existing.expiryEpochMs,
                    existing.accounts, null);
        });
        clearServices(userId);
        clearTroubleTickets(userId);
    }

    public void saveServices(String userId, List<ServiceSummary> services) {
        servicesByUser.put(userId, services == null ? List.of() : List.copyOf(services));
    }

    public List<ServiceSummary> getServices(String userId) {
        return servicesByUser.getOrDefault(userId, List.of());
    }

    public void clearServices(String userId) {
        servicesByUser.remove(userId);
    }

    public void saveTroubleTickets(String userId, List<TroubleTicketSummary> tickets) {
        ticketsByUser.put(userId, tickets == null ? List.of() : List.copyOf(tickets));
    }

    public List<TroubleTicketSummary> getTroubleTickets(String userId) {
        return ticketsByUser.getOrDefault(userId, List.of());
    }

    public void clearTroubleTickets(String userId) {
        ticketsByUser.remove(userId);
    }

    public void clearSession(String userId) {
        byUser.remove(userId);
        servicesByUser.remove(userId);
        ticketsByUser.remove(userId);
        languageByUser.remove(userId);
        menuPathByUser.remove(userId);
        awaitingLanguageSelectionByUser.remove(userId);
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
        clearServices(userId);
        clearTroubleTickets(userId);
        languageByUser.remove(userId);
        menuPathByUser.remove(userId);
        awaitingLanguageSelectionByUser.remove(userId);
        return byUser.remove(userId) != null;
    }

    public String getLanguage(String userId, String defaultLanguage) {
        return languageByUser.getOrDefault(userId, defaultLanguage);
    }

    public void setLanguage(String userId, String language) {
        if (language == null || language.isBlank()) {
            languageByUser.remove(userId);
            return;
        }
        languageByUser.put(userId, language);
    }

    public void setAwaitingLanguageSelection(String userId, boolean awaiting) {
        if (!awaiting) {
            awaitingLanguageSelectionByUser.remove(userId);
            return;
        }
        awaitingLanguageSelectionByUser.put(userId, true);
    }

    public boolean isAwaitingLanguageSelection(String userId) {
        return awaitingLanguageSelectionByUser.getOrDefault(userId, false);
    }

    public void resetBusinessMenu(String userId, String rootMenuId) {
        menuPathByUser.put(userId, new ArrayList<>(List.of(rootMenuId)));
    }

    public String currentBusinessMenu(String userId, String rootMenuId) {
        List<String> path = ensureMenuPath(userId, rootMenuId);
        return path.get(path.size() - 1);
    }

    public void enterBusinessMenu(String userId, String menuId, String rootMenuId) {
        menuPathByUser.compute(userId, (id, existing) -> {
            List<String> path = (existing == null || existing.isEmpty())
                    ? new ArrayList<>(List.of(rootMenuId))
                    : new ArrayList<>(existing);
            path.add(menuId);
            return path;
        });
    }

    public boolean goUpBusinessMenu(String userId, String rootMenuId) {
        final boolean[] moved = {false};
        menuPathByUser.compute(userId, (id, existing) -> {
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

    public int getBusinessMenuDepth(String userId, String rootMenuId) {
        List<String> path = ensureMenuPath(userId, rootMenuId);
        return Math.max(0, path.size() - 1);
    }

    private List<String> ensureMenuPath(String userId, String rootMenuId) {
        return menuPathByUser.compute(userId, (id, existing) -> {
            if (existing == null || existing.isEmpty()) {
                return new ArrayList<>(List.of(rootMenuId));
            }
            return existing;
        });
    }
}

