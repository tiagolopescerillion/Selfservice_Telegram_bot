package com.selfservice.telegrambot.service.dto;

import java.util.List;

/** Summary data for an account returned by the findUser API. */
public record AccountSummary(String accountId, String accountName) {
    private static final int DISPLAY_NAME_MAX = 10;

    public String truncatedName() {
        if (accountName == null) {
            return "<unknown>";
        }
        return accountName.length() > DISPLAY_NAME_MAX
                ? accountName.substring(0, DISPLAY_NAME_MAX)
                : accountName;
    }

    public String displayLabel() {
        String namePart = truncatedName();
        if (namePart.isBlank()) {
            return accountId;
        }
        return accountId + " - " + namePart;
    }
}

/**
 * Encapsulates the parsed outcome of the APIMAN findUser call.
 */
public record FindUserResult(boolean success, String summary, List<AccountSummary> accounts) {
}

