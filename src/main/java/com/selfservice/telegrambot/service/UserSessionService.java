package com.selfservice.telegrambot.service;

import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.InvoiceSummary;
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
        public final String exchangeId;

        public TokenInfo(String accessToken, String refreshToken, String idToken, long expiryEpochMs,
                List<AccountSummary> accounts, AccountSummary selectedAccount, String exchangeId) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.idToken = idToken;
            this.expiryEpochMs = expiryEpochMs;
            this.accounts = accounts;
            this.selectedAccount = selectedAccount;
            this.exchangeId = exchangeId;
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
    private final Map<Long, ServiceSummary> selectedServiceByChat = new ConcurrentHashMap<>();
    private final Map<Long, List<TroubleTicketSummary>> ticketsByChat = new ConcurrentHashMap<>();
    private final Map<Long, List<InvoiceSummary>> invoicesByChat = new ConcurrentHashMap<>();
    private final Map<Long, InvoiceSummary> selectedInvoiceByChat = new ConcurrentHashMap<>();
    private final Map<Long, String> invoiceActionsMenuByChat = new ConcurrentHashMap<>();
    private final Map<Long, String> languageByChat = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> menuPathByChat = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> loginMenuPathByChat = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> optInByChat = new ConcurrentHashMap<>();
    private final Map<Long, String> menuContextByChat = new ConcurrentHashMap<>();
    private final Map<Long, PendingFunctionMenu> pendingFunctionMenusByChat = new ConcurrentHashMap<>();

    public void save(long chatId, String accessToken, String refreshToken, String idToken, long expiresInSeconds,
            String exchangeId) {
        long exp = System.currentTimeMillis() + expiresInSeconds * 1000L;
        byChat.put(chatId, new TokenInfo(accessToken, refreshToken, idToken, exp, Collections.emptyList(), null,
                exchangeId));
        clearServices(chatId);
        clearTroubleTickets(chatId);
        clearInvoices(chatId);
        clearSelectedService(chatId);
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

    public String getExchangeId(long chatId) {
        TokenInfo ti = byChat.get(chatId);
        if (ti == null) {
            return null;
        }
        if (ti.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byChat.remove(chatId);
            return null;
        }
        return ti.exchangeId;
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
            return new TokenInfo(existing.accessToken, existing.refreshToken, existing.idToken, existing.expiryEpochMs, copy,
                    selected, existing.exchangeId);
        });
        AccountSummary selectedAccount = getSelectedAccount(chatId);
        boolean selectedAccountPresent = selectedAccount != null
                && copy.stream().anyMatch(a -> a.accountId().equals(selectedAccount.accountId()));
        if (!selectedAccountPresent) {
            clearSelectedService(chatId);
        }
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
                    matched, existing.exchangeId);
        });
        clearServices(chatId);
        clearTroubleTickets(chatId);
        clearInvoices(chatId);
        clearSelectedService(chatId);
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
                    null, existing.exchangeId);
        });
        clearServices(chatId);
        clearTroubleTickets(chatId);
        clearInvoices(chatId);
        clearSelectedService(chatId);
    }

    public void saveServices(long chatId, List<ServiceSummary> services) {
        List<ServiceSummary> copy = services == null ? List.of() : List.copyOf(services);
        servicesByChat.put(chatId, copy);
        ServiceSummary currentSelection = selectedServiceByChat.get(chatId);
        if (copy.size() == 1) {
            selectedServiceByChat.put(chatId, copy.get(0));
        } else if (currentSelection != null) {
            boolean stillPresent = copy.stream()
                    .anyMatch(s -> s.productId().equals(currentSelection.productId()));
            if (!stillPresent) {
                selectedServiceByChat.remove(chatId);
            }
        } else {
            selectedServiceByChat.remove(chatId);
        }
    }

    public List<ServiceSummary> getServices(long chatId) {
        return servicesByChat.getOrDefault(chatId, List.of());
    }

    public void clearServices(long chatId) {
        servicesByChat.remove(chatId);
        clearSelectedService(chatId);
    }

    public ServiceSummary getSelectedService(long chatId) {
        return selectedServiceByChat.get(chatId);
    }

    public void selectService(long chatId, ServiceSummary service) {
        if (service == null) {
            return;
        }
        List<ServiceSummary> services = servicesByChat.getOrDefault(chatId, List.of());
        ServiceSummary matched = services.stream()
                .filter(s -> s.productId().equals(service.productId()))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            selectedServiceByChat.put(chatId, matched);
        }
    }

    public void clearSelectedService(long chatId) {
        selectedServiceByChat.remove(chatId);
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

    public void saveInvoices(long chatId, List<InvoiceSummary> invoices) {
        invoicesByChat.put(chatId, invoices == null ? List.of() : List.copyOf(invoices));
        InvoiceSummary currentSelection = selectedInvoiceByChat.get(chatId);
        if (invoices != null && invoices.size() == 1) {
            selectedInvoiceByChat.put(chatId, invoices.get(0));
        } else if (currentSelection != null) {
            boolean stillPresent = invoices != null && invoices.stream()
                    .anyMatch(inv -> inv.id().equals(currentSelection.id()));
            if (!stillPresent) {
                selectedInvoiceByChat.remove(chatId);
            }
        } else {
            selectedInvoiceByChat.remove(chatId);
        }
    }

    public List<InvoiceSummary> getInvoices(long chatId) {
        return invoicesByChat.getOrDefault(chatId, List.of());
    }

    public void setInvoiceActionsMenu(long chatId, String menuId) {
        if (menuId == null || menuId.isBlank()) {
            invoiceActionsMenuByChat.remove(chatId);
        } else {
            invoiceActionsMenuByChat.put(chatId, menuId);
        }
    }

    public String getInvoiceActionsMenu(long chatId) {
        return invoiceActionsMenuByChat.get(chatId);
    }

    public void selectInvoice(long chatId, InvoiceSummary invoice) {
        if (invoice == null) {
            return;
        }
        List<InvoiceSummary> invoices = invoicesByChat.getOrDefault(chatId, List.of());
        InvoiceSummary matched = invoices.stream()
                .filter(inv -> inv.id().equals(invoice.id()))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            selectedInvoiceByChat.put(chatId, matched);
        }
    }

    public InvoiceSummary getSelectedInvoice(long chatId) {
        return selectedInvoiceByChat.get(chatId);
    }

    public void clearInvoices(long chatId) {
        invoicesByChat.remove(chatId);
        selectedInvoiceByChat.remove(chatId);
        invoiceActionsMenuByChat.remove(chatId);
    }

    public void clearSession(long chatId) {
        byChat.remove(chatId);
        servicesByChat.remove(chatId);
        selectedServiceByChat.remove(chatId);
        ticketsByChat.remove(chatId);
        invoicesByChat.remove(chatId);
        selectedInvoiceByChat.remove(chatId);
        invoiceActionsMenuByChat.remove(chatId);
        languageByChat.remove(chatId);
        menuPathByChat.remove(chatId);
        optInByChat.remove(chatId);
        menuContextByChat.remove(chatId);
        pendingFunctionMenusByChat.remove(chatId);
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
        clearMenuContext(chatId);
        clearPendingFunctionMenu(chatId);
    }

    public void resetLoginMenu(long chatId, String rootMenuId) {
        loginMenuPathByChat.put(chatId, new ArrayList<>(List.of(rootMenuId)));
    }

    public boolean isOptedIn(long chatId) {
        return optInByChat.getOrDefault(chatId, false);
    }

    public void setOptIn(long chatId, boolean optIn) {
        if (optIn) {
            optInByChat.put(chatId, true);
        } else {
            optInByChat.remove(chatId);
        }
    }

    public String currentBusinessMenu(long chatId, String rootMenuId) {
        List<String> path = ensureMenuPath(chatId, rootMenuId);
        return path.get(path.size() - 1);
    }

    public String currentLoginMenu(long chatId, String rootMenuId) {
        List<String> path = ensureLoginMenuPath(chatId, rootMenuId);
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
        clearMenuContext(chatId);
        clearPendingFunctionMenu(chatId);
    }

    public void enterLoginMenu(long chatId, String menuId, String rootMenuId) {
        loginMenuPathByChat.compute(chatId, (id, existing) -> {
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
        if (moved[0]) {
            clearMenuContext(chatId);
            clearPendingFunctionMenu(chatId);
        }
        return moved[0];
    }

    public boolean goUpLoginMenu(long chatId, String rootMenuId) {
        final boolean[] moved = {false};
        loginMenuPathByChat.compute(chatId, (id, existing) -> {
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

    public void setMenuContext(long chatId, String contextMessage) {
        if (contextMessage == null || contextMessage.isBlank()) {
            menuContextByChat.remove(chatId);
            return;
        }
        menuContextByChat.put(chatId, contextMessage);
    }

    public String getMenuContext(long chatId) {
        return menuContextByChat.get(chatId);
    }

    public void clearMenuContext(long chatId) {
        menuContextByChat.remove(chatId);
    }

    public void setPendingFunctionMenu(long chatId, String submenuId, String contextLabel, List<String> options) {
        if (options == null || options.isEmpty()) {
            pendingFunctionMenusByChat.remove(chatId);
            return;
        }
        pendingFunctionMenusByChat.put(chatId, new PendingFunctionMenu(submenuId, contextLabel, List.copyOf(options)));
    }

    public PendingFunctionMenu consumePendingFunctionMenu(long chatId, String selection) {
        PendingFunctionMenu pending = pendingFunctionMenusByChat.get(chatId);
        if (pending == null || selection == null || selection.isBlank()) {
            return null;
        }
        boolean matched = pending.options().stream()
                .anyMatch(option -> option.equalsIgnoreCase(selection.trim()));
        if (!matched) {
            return null;
        }
        pendingFunctionMenusByChat.remove(chatId);
        return pending;
    }

    public void clearPendingFunctionMenu(long chatId) {
        pendingFunctionMenusByChat.remove(chatId);
    }

    public record PendingFunctionMenu(String submenuId, String contextLabel, List<String> options) { }

    public int getBusinessMenuDepth(long chatId, String rootMenuId) {
        List<String> path = ensureMenuPath(chatId, rootMenuId);
        return Math.max(0, path.size() - 1);
    }

    public int getLoginMenuDepth(long chatId, String rootMenuId) {
        List<String> path = ensureLoginMenuPath(chatId, rootMenuId);
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

    private List<String> ensureLoginMenuPath(long chatId, String rootMenuId) {
        return loginMenuPathByChat.compute(chatId, (id, existing) -> {
            if (existing == null || existing.isEmpty()) {
                return new ArrayList<>(List.of(rootMenuId));
            }
            return existing;
        });
    }
}
