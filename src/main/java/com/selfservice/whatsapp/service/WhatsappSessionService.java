package com.selfservice.whatsapp.service;

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
public class WhatsappSessionService {

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

    private final Map<String, TokenInfo> byUser = new ConcurrentHashMap<>();
    private final Map<String, List<ServiceSummary>> servicesByUser = new ConcurrentHashMap<>();
    private final Map<String, ServiceSummary> selectedServiceByUser = new ConcurrentHashMap<>();
    private final Map<String, List<InvoiceSummary>> invoicesByUser = new ConcurrentHashMap<>();
    private final Map<String, InvoiceSummary> selectedInvoiceByUser = new ConcurrentHashMap<>();
    private final Map<String, String> invoiceActionsMenuByUser = new ConcurrentHashMap<>();
    private final Map<String, List<TroubleTicketSummary>> ticketsByUser = new ConcurrentHashMap<>();
    private final Map<String, String> languageByUser = new ConcurrentHashMap<>();
    private final Map<String, List<String>> menuPathByUser = new ConcurrentHashMap<>();
    private final Map<String, List<String>> loginMenuPathByUser = new ConcurrentHashMap<>();
    private final Map<String, Boolean> awaitingLanguageSelectionByUser = new ConcurrentHashMap<>();
    private final Map<String, SelectionContext> selectionContextByUser = new ConcurrentHashMap<>();
    private final Map<String, Integer> selectionPageStartByUser = new ConcurrentHashMap<>();
    private final Map<String, Boolean> optInByUser = new ConcurrentHashMap<>();

    public enum SelectionContext {
        NONE,
        ACCOUNT,
        SERVICE,
        INVOICE,
        INVOICE_ACTION,
        TICKET,
        OPT_IN,
        SETTINGS
    }

    public void save(String userId, String accessToken, String refreshToken, String idToken, long expiresInSeconds,
            String exchangeId) {
        long exp = System.currentTimeMillis() + expiresInSeconds * 1000L;
        byUser.put(userId, new TokenInfo(accessToken, refreshToken, idToken, exp, Collections.emptyList(), null,
                exchangeId));
        clearServices(userId);
        clearTroubleTickets(userId);
        clearSelectedService(userId);
    }

    public TokenSnapshot getTokenSnapshot(String userId) {
        TokenInfo info = byUser.get(userId);
        if (info == null) {
            return TokenSnapshot.none();
        }
        boolean expired = info.expiryEpochMs <= System.currentTimeMillis() + 30_000;
        return new TokenSnapshot(expired ? TokenState.EXPIRED : TokenState.VALID, info.accessToken);
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

    public String getExchangeId(String userId) {
        TokenInfo info = byUser.get(userId);
        if (info == null) {
            return null;
        }
        if (info.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byUser.remove(userId);
            return null;
        }
        return info.exchangeId;
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
                    selected, existing.exchangeId);
        });
        AccountSummary selectedAccount = getSelectedAccount(userId);
        boolean selectedAccountPresent = selectedAccount != null
                && copy.stream().anyMatch(a -> a.accountId().equals(selectedAccount.accountId()));
        if (!selectedAccountPresent) {
            clearSelectedService(userId);
        }
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
                    matched, existing.exchangeId);
        });
        clearServices(userId);
        clearTroubleTickets(userId);
        clearSelectedService(userId);
        clearInvoices(userId);
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
                    existing.accounts, null, existing.exchangeId);
        });
        clearServices(userId);
        clearTroubleTickets(userId);
        clearSelectedService(userId);
        clearInvoices(userId);
    }

    public void saveServices(String userId, List<ServiceSummary> services) {
        List<ServiceSummary> copy = services == null ? List.of() : List.copyOf(services);
        servicesByUser.put(userId, copy);
        ServiceSummary currentSelection = selectedServiceByUser.get(userId);
        if (copy.size() == 1) {
            selectedServiceByUser.put(userId, copy.get(0));
        } else if (currentSelection != null) {
            boolean stillPresent = copy.stream()
                    .anyMatch(s -> s.productId().equals(currentSelection.productId()));
            if (!stillPresent) {
                selectedServiceByUser.remove(userId);
            }
        } else {
            selectedServiceByUser.remove(userId);
        }
    }

    public List<ServiceSummary> getServices(String userId) {
        return servicesByUser.getOrDefault(userId, List.of());
    }

    public void clearServices(String userId) {
        servicesByUser.remove(userId);
        clearSelectedService(userId);
    }

    public ServiceSummary getSelectedService(String userId) {
        return selectedServiceByUser.get(userId);
    }

    public void selectService(String userId, ServiceSummary service) {
        if (service == null) {
            return;
        }
        List<ServiceSummary> services = servicesByUser.getOrDefault(userId, List.of());
        ServiceSummary matched = services.stream()
                .filter(s -> s.productId().equals(service.productId()))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            selectedServiceByUser.put(userId, matched);
        }
    }

    public void clearSelectedService(String userId) {
        selectedServiceByUser.remove(userId);
    }

    public void saveInvoices(String userId, List<InvoiceSummary> invoices) {
        List<InvoiceSummary> copy = invoices == null ? List.of() : List.copyOf(invoices);
        invoicesByUser.put(userId, copy);
        InvoiceSummary currentSelection = selectedInvoiceByUser.get(userId);
        if (copy.size() == 1) {
            selectedInvoiceByUser.put(userId, copy.get(0));
        } else if (currentSelection != null) {
            boolean stillPresent = copy.stream()
                    .anyMatch(inv -> inv.id().equals(currentSelection.id()));
            if (!stillPresent) {
                selectedInvoiceByUser.remove(userId);
            }
        } else {
            selectedInvoiceByUser.remove(userId);
        }
    }

    public List<InvoiceSummary> getInvoices(String userId) {
        return invoicesByUser.getOrDefault(userId, List.of());
    }

    public void setInvoiceActionsMenu(String userId, String menuId) {
        if (menuId == null || menuId.isBlank()) {
            invoiceActionsMenuByUser.remove(userId);
        } else {
            invoiceActionsMenuByUser.put(userId, menuId);
        }
    }

    public String getInvoiceActionsMenu(String userId) {
        return invoiceActionsMenuByUser.get(userId);
    }

    public void clearInvoices(String userId) {
        invoicesByUser.remove(userId);
        clearSelectedInvoice(userId);
        invoiceActionsMenuByUser.remove(userId);
    }

    public InvoiceSummary getSelectedInvoice(String userId) {
        return selectedInvoiceByUser.get(userId);
    }

    public void selectInvoice(String userId, InvoiceSummary invoice) {
        if (invoice == null) {
            return;
        }
        List<InvoiceSummary> invoices = invoicesByUser.getOrDefault(userId, List.of());
        InvoiceSummary matched = invoices.stream()
                .filter(inv -> inv.id().equals(invoice.id()))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            selectedInvoiceByUser.put(userId, matched);
        }
    }

    public void clearSelectedInvoice(String userId) {
        selectedInvoiceByUser.remove(userId);
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
        selectedServiceByUser.remove(userId);
        invoicesByUser.remove(userId);
        selectedInvoiceByUser.remove(userId);
        invoiceActionsMenuByUser.remove(userId);
        ticketsByUser.remove(userId);
        languageByUser.remove(userId);
        menuPathByUser.remove(userId);
        awaitingLanguageSelectionByUser.remove(userId);
        selectionContextByUser.remove(userId);
        selectionPageStartByUser.remove(userId);
        optInByUser.remove(userId);
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
        clearInvoices(userId);
        clearTroubleTickets(userId);
        languageByUser.remove(userId);
        menuPathByUser.remove(userId);
        awaitingLanguageSelectionByUser.remove(userId);
        selectionContextByUser.remove(userId);
        selectionPageStartByUser.remove(userId);
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

    public boolean isOptedIn(String userId) {
        return optInByUser.getOrDefault(userId, false);
    }

    public void setOptIn(String userId, boolean optIn) {
        if (optIn) {
            optInByUser.put(userId, true);
        } else {
            optInByUser.remove(userId);
        }
    }

    public boolean isAwaitingLanguageSelection(String userId) {
        return awaitingLanguageSelectionByUser.getOrDefault(userId, false);
    }

    public void setSelectionContext(String userId, SelectionContext context) {
        setSelectionContext(userId, context, 0);
    }

    public void setSelectionContext(String userId, SelectionContext context, int pageStartIndex) {
        if (context == null || context == SelectionContext.NONE) {
            selectionContextByUser.remove(userId);
            selectionPageStartByUser.remove(userId);
            return;
        }
        selectionContextByUser.put(userId, context);
        selectionPageStartByUser.put(userId, Math.max(0, pageStartIndex));
    }

    public SelectionContext getSelectionContext(String userId) {
        return selectionContextByUser.getOrDefault(userId, SelectionContext.NONE);
    }

    public int getSelectionPageStart(String userId) {
        return selectionPageStartByUser.getOrDefault(userId, 0);
    }

    public void resetBusinessMenu(String userId, String rootMenuId) {
        menuPathByUser.put(userId, new ArrayList<>(List.of(rootMenuId)));
    }

    public void resetLoginMenu(String userId, String rootMenuId) {
        loginMenuPathByUser.put(userId, new ArrayList<>(List.of(rootMenuId)));
    }

    public String currentBusinessMenu(String userId, String rootMenuId) {
        List<String> path = ensureMenuPath(userId, rootMenuId);
        return path.get(path.size() - 1);
    }

    public String currentLoginMenu(String userId, String rootMenuId) {
        List<String> path = ensureLoginMenuPath(userId, rootMenuId);
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

    public void enterLoginMenu(String userId, String menuId, String rootMenuId) {
        loginMenuPathByUser.compute(userId, (id, existing) -> {
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

    public boolean goUpLoginMenu(String userId, String rootMenuId) {
        final boolean[] moved = {false};
        loginMenuPathByUser.compute(userId, (id, existing) -> {
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

    public int getLoginMenuDepth(String userId, String rootMenuId) {
        List<String> path = ensureLoginMenuPath(userId, rootMenuId);
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

    private List<String> ensureLoginMenuPath(String userId, String rootMenuId) {
        return loginMenuPathByUser.compute(userId, (id, existing) -> {
            if (existing == null || existing.isEmpty()) {
                return new ArrayList<>(List.of(rootMenuId));
            }
            return existing;
        });
    }
}

