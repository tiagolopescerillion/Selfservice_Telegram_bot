package com.selfservice.application.dto;

import java.util.List;

/** Wrapper around the list of services plus optional error message. */
public record ServiceListResult(List<ServiceSummary> services, String errorMessage) {
    public ServiceListResult {
        services = services == null ? List.of() : List.copyOf(services);
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}
