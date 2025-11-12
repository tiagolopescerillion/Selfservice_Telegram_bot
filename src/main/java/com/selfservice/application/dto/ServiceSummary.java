package com.selfservice.application.dto;

/** Lightweight representation of a customer's main service. */
public record ServiceSummary(String productId, String productName, String accessNumber) {

    public String displayLabel() {
        String name = (productName == null || productName.isBlank()) ? "<unknown>" : productName.strip();
        String number = (accessNumber == null || accessNumber.isBlank()) ? "<no number>" : accessNumber.strip();
        return name + " (" + number + ")";
    }
}
