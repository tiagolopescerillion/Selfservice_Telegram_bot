package com.selfservice.application.dto;

import java.util.Map;

public record QueryParamExportRequest(Map<String, Map<String, String>> updates) {
}

