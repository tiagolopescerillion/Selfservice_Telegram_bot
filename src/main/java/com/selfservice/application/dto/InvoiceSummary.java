package com.selfservice.application.dto;

/** Simplified invoice summary used for messaging channels. */
public record InvoiceSummary(String id, String billDate, String totalAmount, String unpaidAmount) { }

