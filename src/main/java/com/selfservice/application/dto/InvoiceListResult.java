package com.selfservice.application.dto;

import java.util.List;

/** Wrapper around the list of invoices plus optional error message. */
public record InvoiceListResult(List<InvoiceSummary> invoices, String errorMessage) {
    public InvoiceListResult {
        invoices = invoices == null ? List.of() : List.copyOf(invoices);
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}

