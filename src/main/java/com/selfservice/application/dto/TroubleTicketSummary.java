package com.selfservice.application.dto;

/** Summary information for a trouble ticket shown in the Telegram bot. */
public record TroubleTicketSummary(String id, String status, String description) {
    public String descriptionOrFallback() {
        if (description == null || description.isBlank()) {
            return "<no description provided>";
        }
        return description;
    }
}
