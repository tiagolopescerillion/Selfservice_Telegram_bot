package com.selfservice.application.dto;

import java.util.List;
import java.util.Map;

public record QueryParamExportRequest(Map<String, Map<String, String>> updates,
                                     List<ServiceFunctionConfig> serviceFunctions) {
}

