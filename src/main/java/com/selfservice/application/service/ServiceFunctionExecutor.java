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
            return ExecutionResult.handled("Service call succeeded but returned an empty response.", ResponseMode.TEXT,
                    null);
        }

        JsonBody jsonBody = parseBody(response.body(), response.headers().getContentType());

        if (definition.responseTemplate() == ServiceCatalog.ResponseTemplate.JSON) {
            log.info("Service '{}' response: {}", callbackId, jsonBody.prettyBody);
            return ExecutionResult.handled("Service response recorded in logs.", ResponseMode.SILENT, null);
        }

        String rendered = renderOutput(definition.output(), jsonBody);
        if (definition.responseTemplate() == ServiceCatalog.ResponseTemplate.CARD) {
            return ExecutionResult.handled(rendered, ResponseMode.CARD, rendered);
        }

        return ExecutionResult.handled(rendered, ResponseMode.TEXT, null);
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

    private JsonBody parseBody(String body, MediaType contentType) {
        if (contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            try {
                JsonNode node = objectMapper.readTree(body);
                String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
                return new JsonBody(node, pretty);
            } catch (Exception e) {
                log.debug("Failed to parse JSON response", e);
            }
        }
        return new JsonBody(null, body);
    }

    private String renderOutput(String outputSpec, JsonBody body) {
        if (body == null) {
            return "";
        }

        if (outputSpec == null || outputSpec.isBlank()) {
            return body.prettyBody;
        }

        if (body.node == null) {
            return outputSpec;
        }

        String[] paths = outputSpec.split(",");
        if (body.node.isArray()) {
            StringBuilder aggregated = new StringBuilder();
            int idx = 1;
            for (JsonNode element : body.node) {
                String rendered = renderFields(paths, element);
                if (rendered.isEmpty()) {
                    continue;
                }
                if (!aggregated.isEmpty()) {
                    aggregated.append("\n\n");
                }
                if (body.node.size() > 1) {
                    aggregated.append("Item ").append(idx++).append(':').append('\n');
                }
                aggregated.append(rendered);
            }
            if (aggregated.length() == 0) {
                return "No data available.";
            }
            return aggregated.toString();
        }

        String rendered = renderFields(paths, body.node);
        if (rendered.isEmpty()) {
            return "No data available.";
        }
        return rendered;
    }

    private String renderFields(String[] paths, JsonNode root) {
        StringBuilder builder = new StringBuilder();
        for (String rawPath : paths) {
            String path = rawPath.trim();
            if (path.isEmpty()) {
                continue;
            }
            JsonNode value = resolvePath(root, path);
            if (value != null && !value.isMissingNode()) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(path).append(": ");
                builder.append(value.isTextual() ? value.asText() : value.toString());
            }
        }
        return builder.toString();
    }

    private JsonNode resolvePath(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            String segment = part.trim();
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.matches(".+\\[\\d+\\]$")) {
                int bracket = segment.lastIndexOf('[');
                String field = segment.substring(0, bracket);
                int index = Integer.parseInt(segment.substring(bracket + 1, segment.length() - 1));
                current = current.path(field);
                if (current.isArray() && current.size() > index) {
                    current = current.get(index);
                } else {
                    return null;
                }
            } else {
                current = current.path(segment);
            }
        }
        return current;
    }

    public enum ResponseMode { TEXT, CARD, SILENT }

    public record ExecutionResult(boolean handled, String message, ResponseMode mode, String buttonLabel) {
        public static ExecutionResult handled(String message) {
            return new ExecutionResult(true, message, ResponseMode.TEXT, null);
        }

        public static ExecutionResult handled(String message, ResponseMode mode, String buttonLabel) {
            return new ExecutionResult(true, message, mode, buttonLabel);
        }

        public static ExecutionResult notHandled() {
            return new ExecutionResult(false, null, ResponseMode.TEXT, null);
        }
    }

    private record JsonBody(JsonNode node, String prettyBody) { }
}
