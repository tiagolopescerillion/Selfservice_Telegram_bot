package com.selfservice.application.dto;

import java.util.Map;

public record ServiceFunctionConfig(String name, String endpointKey, Map<String, String> queryParams,
                                    boolean accountContext, boolean serviceContext) {
}
