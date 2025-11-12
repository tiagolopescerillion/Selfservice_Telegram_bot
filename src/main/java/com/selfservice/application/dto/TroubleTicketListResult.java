package com.selfservice.application.dto;

import java.util.List;

/** Wrapper around the list of tickets plus optional error message. */
public record TroubleTicketListResult(List<TroubleTicketSummary> tickets, String errorMessage) {
    public TroubleTicketListResult {
        tickets = tickets == null ? List.of() : List.copyOf(tickets);
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}
