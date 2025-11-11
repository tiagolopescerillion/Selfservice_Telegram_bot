package com.selfservice.telegrambot.service.dto;

import java.util.List;

/**
 * Encapsulates the parsed outcome of the APIMAN findUser call.
 */
public record FindUserResult(boolean success, String summary, List<AccountSummary> accounts) {
}

