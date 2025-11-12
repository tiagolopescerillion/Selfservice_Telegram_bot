package com.selfservice.application.dto;

import java.util.List;

/**
 * Encapsulates the parsed outcome of the APIMAN findUser call.
 */
public record FindUserResult(boolean success, String summary, List<AccountSummary> accounts, String givenName) {
}

