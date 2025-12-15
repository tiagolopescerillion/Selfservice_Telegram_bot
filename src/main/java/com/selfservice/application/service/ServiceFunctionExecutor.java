package com.selfservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfservice.application.config.ApiRegistry;
import com.selfservice.application.config.ServiceCatalog;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.ServiceSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Executes Service Builder functions by resolving their configured API endpoint,
 * applying query parameters, and formatting the response for chat channels.
 */
@Service
public class ServiceFunctionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ServiceFunctionExecutor.class);

    private final ApiRegistry apiRegistry;
    private final ServiceCatalog serviceCatalog;
    private final CommonApiService commonApiService;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    public ServiceFunctionExecutor(ApiRegistry apiRegistry,
            ServiceCatalog serviceCatalog,
            CommonApiService commonApiService,
            Environment environment,
            ObjectMapper objectMapper) {
        this.apiRegistry = apiRegistry;
        this.serviceCatalog = serviceCatalog;
        this.commonApiService = commonApiService;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    /**
     * Attempts to execute a configured Service Builder function.
     *
     * @param callbackId callback/function identifier received from the chat channel
     * @param accessToken bearer token for downstream API calls
     * @param account selected account context (may be null)
     * @param service selected service context (may be null)
     * @return result indicating if the callback was handled and the formatted response text
     */
    public ExecutionResult execute(String callbackId, String accessToken, AccountSummary account,
            ServiceSummary service) {
        Optional<ServiceCatalog.ServiceDefinition> maybeDefinition = serviceCatalog.findByName(callbackId);
        if (maybeDefinition.isEmpty()) {
            return ExecutionResult.notHandled();
        }

        ServiceCatalog.ServiceDefinition definition = maybeDefinition.get();
        if (definition.responseTemplate() == ServiceCatalog.ResponseTemplate.EXISTING) {
            return ExecutionResult.notHandled();
        }

        Optional<ApiRegistry.ApiDefinition> maybeApi = apiRegistry.findByName(definition.apiName());
        if (maybeApi.isEmpty()) {
            log.warn("Service '{}' references unknown API '{}'.", callbackId, definition.apiName());
            return ExecutionResult.handled("Service is not available: missing API definition.");
        }

        String url = resolvePlaceholders(maybeApi.get().url());
        Map<String, String> query = buildQuery(definition, account, service);

        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(url, HttpMethod.GET, accessToken, query, new HttpHeaders(), null));

        if (!response.success()) {
            String error = response.statusCode() == 0
                    ? response.errorMessage()
                    : ("HTTP " + response.statusCode());
            return ExecutionResult.handled("Service call failed: " + error);
        }

        if (response.body() == null || response.body().isBlank()) {
            return ExecutionResult.handled("Service call succeeded but returned an empty response.");
        }

        String formatted = formatBody(response.body(), response.headers().getContentType());
        return ExecutionResult.handled(formatted);
    }

    private Map<String, String> buildQuery(ServiceCatalog.ServiceDefinition definition, AccountSummary account,
            ServiceSummary service) {
        Map<String, String> params = new LinkedHashMap<>();
        if (definition.queryParameters() != null) {
            definition.queryParameters().forEach((key, value) -> params.put(key, resolvePlaceholders(value)));
        }

        if (account != null) {
            params.replaceAll((k, v) -> v == null || v.isBlank() ? substituteAccount(k, v, account) : v);
        }
        if (service != null) {
            params.replaceAll((k, v) -> v == null || v.isBlank() ? substituteService(k, v, service) : v);
        }
        return params;
    }

    private String substituteAccount(String key, String current, AccountSummary account) {
        if (key == null) {
            return current;
        }
        if (key.contains("billingAccount.id") || key.endsWith("accountId") || key.endsWith("account.id")) {
            return account.accountId();
        }
        return current;
    }

    private String substituteService(String key, String current, ServiceSummary service) {
        if (key == null) {
            return current;
        }
        if (key.contains("product.id") || key.endsWith("serviceId") || key.endsWith("productId")) {
            return service.productId();
        }
        return current;
    }

    private String resolvePlaceholders(String value) {
        if (value == null) {
            return null;
        }
        try {
            return environment == null ? value : environment.resolvePlaceholders(value);
        } catch (Exception e) {
            return value;
        }
    }

    private String formatBody(String body, MediaType contentType) {
        if (contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            try {
                JsonNode node = objectMapper.readTree(body);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (Exception e) {
                log.debug("Failed to pretty-print JSON response", e);
                return body;
            }
        }
        return body;
    }

    public record ExecutionResult(boolean handled, String message) {
        public static ExecutionResult handled(String message) {
            return new ExecutionResult(true, message);
        }

        public static ExecutionResult notHandled() {
            return new ExecutionResult(false, null);
        }
    }
}
