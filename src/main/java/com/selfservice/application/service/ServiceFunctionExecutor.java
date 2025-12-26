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
import org.springframework.web.util.UriComponentsBuilder;

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
    private final ContextTraceLogger contextTraceLogger;

    public ServiceFunctionExecutor(ApiRegistry apiRegistry,
            ServiceCatalog serviceCatalog,
            CommonApiService commonApiService,
            Environment environment,
            ObjectMapper objectMapper,
            ContextTraceLogger contextTraceLogger) {
        this.apiRegistry = apiRegistry;
        this.serviceCatalog = serviceCatalog;
        this.commonApiService = commonApiService;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.contextTraceLogger = contextTraceLogger;
    }

    /**
     * Attempts to execute a configured Service Builder function.
     *
     * @param callbackId callback/function identifier received from the chat channel
     * @param accessToken bearer token for downstream API calls
     * @param account selected account context (may be null)
     * @param service selected service context (may be null)
     * @param objectContextValue selected object context value (may be null)
     * @return result indicating if the callback was handled and the formatted response text
     */
    public ExecutionResult execute(String callbackId, String accessToken, AccountSummary account,
            ServiceSummary service, String objectContextValue) {
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
        Map<String, String> query = buildQuery(definition, account, service, objectContextValue);
        String targetUrl = buildTargetUrl(url, query);

        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(url, HttpMethod.GET, accessToken, query, new HttpHeaders(), null));

        logApiTrace(definition.apiName(), targetUrl, response);

        if (!response.success()) {
            logContextTrace(account, service, null);
            String error = response.statusCode() == 0
                    ? response.errorMessage()
                    : ("HTTP " + response.statusCode());
            return ExecutionResult.handled("Service call failed: " + error);
        }

        if (response.body() == null || response.body().isBlank()) {
            logContextTrace(account, service, null);
            return ExecutionResult.handled("Service call succeeded but returned an empty response.", ResponseMode.TEXT,
                    null, null, null, false, null);
        }

        JsonBody jsonBody = parseBody(response.body(), response.headers().getContentType());

        boolean objectContextEnabled = hasObjectContext(definition.outputs());
        String objectContextLabel = resolveObjectContextLabel(definition.outputs());
        int itemCount = jsonBody.node != null && jsonBody.node.isArray() ? jsonBody.node.size() : 1;
        String resolvedObjectContextValue = (itemCount == 1)
                ? extractObjectContext(definition.outputs(), jsonBody)
                : null;
        logContextTrace(account, service, resolvedObjectContextValue);

        if (definition.responseTemplate() == ServiceCatalog.ResponseTemplate.JSON) {
            log.info("Service '{}' response: {}", callbackId, jsonBody.prettyBody);
            return ExecutionResult.handled("Service response recorded in logs.", ResponseMode.SILENT, null, null,
                    null, objectContextEnabled, objectContextLabel);
        }

        RenderResult rendered = renderOutput(definition.outputs(), jsonBody,
                definition.responseTemplate() == ServiceCatalog.ResponseTemplate.CARD);
        String contextLabel = null;
        String messageText = rendered.text();
        if (contextLabel != null && !contextLabel.isBlank()) {
            messageText = contextLabel + " select: " + messageText;
        }
        if (definition.responseTemplate() == ServiceCatalog.ResponseTemplate.CARD) {
            return ExecutionResult.handled(messageText, ResponseMode.CARD, rendered.buttons(), rendered.options(),
                    rendered.contextValues(), objectContextEnabled, objectContextLabel);
        }

        return ExecutionResult.handled(messageText, ResponseMode.TEXT, null, rendered.options(), rendered.contextValues(),
                objectContextEnabled, objectContextLabel);
    }

    private Map<String, String> buildQuery(ServiceCatalog.ServiceDefinition definition, AccountSummary account,
            ServiceSummary service, String objectContextValue) {
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
        if (definition.accountContextField() != null && !definition.accountContextField().isBlank()
                && account != null && account.accountId() != null) {
            params.put(definition.accountContextField(), account.accountId());
        }
        if (definition.serviceContextField() != null && !definition.serviceContextField().isBlank()
                && service != null && service.productId() != null) {
            params.put(definition.serviceContextField(), service.productId());
        }
        if (definition.objectContextField() != null && !definition.objectContextField().isBlank()
                && objectContextValue != null && !objectContextValue.isBlank()) {
            params.put(definition.objectContextField(), objectContextValue);
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

    private String buildTargetUrl(String baseUrl, Map<String, String> params) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "<unknown>";
        }
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
            if (params != null) {
                params.forEach(builder::queryParam);
            }
            return builder.build(true).toUriString();
        } catch (Exception ex) {
            return baseUrl;
        }
    }

    private void logApiTrace(String apiName, String requestUrl, CommonApiService.ApiResponse response) {
        if (!isFlagEnabled("test.api")) {
            return;
        }
        String status = response == null ? "<no-response>" : String.valueOf(response.statusCode());
        String body = response == null ? "<null>" : (response.body() == null ? "<empty>" : response.body());
        log.info("-----------\nAPI: {}\n- Request: {}\n- Response: {} {}\n-----------", apiName, requestUrl, status, body);
    }

    private void logContextTrace(AccountSummary account, ServiceSummary service, String objectContextValue) {
        String accountValue = account == null ? "<none>" : String.valueOf(account.accountId());
        String serviceValue = service == null ? "<none>" : String.valueOf(service.productId());
        String objectValue = (objectContextValue == null || objectContextValue.isBlank()) ? "<none>" : objectContextValue;
        contextTraceLogger.logContext(accountValue, serviceValue, objectValue);
    }

    private boolean isFlagEnabled(String property) {
        if (environment == null || property == null) {
            return false;
        }
        String raw = environment.getProperty(property, "no");
        return "yes".equalsIgnoreCase(raw.trim());
    }

    private boolean hasObjectContext(java.util.List<ServiceCatalog.OutputField> fields) {
        if (fields == null) {
            return false;
        }
        return fields.stream().anyMatch(ServiceCatalog.OutputField::objectContext);
    }

    private String resolveObjectContextLabel(java.util.List<ServiceCatalog.OutputField> fields) {
        if (fields == null) {
            return null;
        }
        return fields.stream()
                .filter(ServiceCatalog.OutputField::objectContext)
                .findFirst()
                .map(field -> (field.label() == null || field.label().isBlank()) ? field.field() : field.label())
                .orElse(null);
    }

    private String extractObjectContext(java.util.List<ServiceCatalog.OutputField> fields, JsonBody body) {
        if (body == null || body.node == null || fields == null) {
            return null;
        }
        return fields.stream()
                .filter(ServiceCatalog.OutputField::objectContext)
                .findFirst()
                .map(field -> {
                    JsonNode root = body.node.isArray() && body.node.size() > 0 ? body.node.get(0) : body.node;
                    JsonNode value = resolvePath(root, field.field());
                    if (value == null || value.isMissingNode() || value.isNull()) {
                        return null;
                    }
                    return value.isTextual() ? value.asText() : value.toString();
                })
                .orElse(null);
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

    private RenderResult renderOutput(java.util.List<ServiceCatalog.OutputField> outputFields, JsonBody body, boolean listMode) {
        if (body == null) {
            return new RenderResult("", java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    java.util.Collections.emptyList());
        }
        java.util.List<ServiceCatalog.OutputField> fields = outputFields == null
                ? java.util.Collections.emptyList()
                : outputFields;

        if (listMode) {
            return renderList(fields, body);
        }

        return renderText(fields, body);
    }

    private RenderResult renderList(java.util.List<ServiceCatalog.OutputField> fields, JsonBody body) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<String> contextValues = new java.util.ArrayList<>();
        if (body.node != null && body.node.isArray()) {
            for (JsonNode element : body.node) {
                String rendered = renderFields(fields, element);
                if (!rendered.isBlank()) {
                    labels.add(rendered);
                    contextValues.add(extractObjectContextFromNode(fields, element));
                }
            }
        }

        if (labels.isEmpty()) {
            String rendered = body.node == null ? body.prettyBody : renderFields(fields, body.node);
            if (rendered == null || rendered.isBlank()) {
                rendered = "No data available.";
            }
            return new RenderResult(rendered, java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    java.util.Collections.emptyList());
        }

        if (labels.size() == 1) {
            return new RenderResult(labels.get(0), java.util.Collections.emptyList(), labels, contextValues);
        }

        return new RenderResult("Select an option:", labels, labels, contextValues);
    }

    private RenderResult renderText(java.util.List<ServiceCatalog.OutputField> fields, JsonBody body) {
        if (fields.isEmpty()) {
            return new RenderResult(body.prettyBody, java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    java.util.Collections.emptyList());
        }
        if (body.node == null) {
            return new RenderResult(body.prettyBody, java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    java.util.Collections.emptyList());
        }
        if (body.node.isArray()) {
            StringBuilder aggregated = new StringBuilder();
            int idx = 1;
            for (JsonNode element : body.node) {
                String rendered = renderFields(fields, element);
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
                return new RenderResult("No data available.", java.util.Collections.emptyList(),
                        java.util.Collections.emptyList(), java.util.Collections.emptyList());
            }
            return new RenderResult(aggregated.toString(), java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), java.util.Collections.emptyList());
        }

        String rendered = renderFields(fields, body.node);
        if (rendered.isEmpty()) {
            rendered = "No data available.";
        }
        return new RenderResult(rendered, java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.emptyList());
    }

    private String renderFields(java.util.List<ServiceCatalog.OutputField> fields, JsonNode root) {
        if (fields == null || fields.isEmpty()) {
            return root == null ? "" : root.toString();
        }
        StringBuilder builder = new StringBuilder();
        for (ServiceCatalog.OutputField field : fields) {
            if (field == null || field.field() == null) {
                continue;
            }
            JsonNode value = resolvePath(root, field.field());
            if (value == null || value.isMissingNode() || value.isNull()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            String label = field.label() == null || field.label().isBlank() ? field.field() : field.label();
            builder.append(label).append(' ');
            builder.append(value.isTextual() ? value.asText() : value.toString());
        }
        return builder.toString();
    }

    private String extractObjectContextFromNode(java.util.List<ServiceCatalog.OutputField> fields, JsonNode root) {
        if (fields == null || fields.isEmpty() || root == null) {
            return null;
        }
        return fields.stream()
                .filter(ServiceCatalog.OutputField::objectContext)
                .findFirst()
                .map(field -> {
                    JsonNode value = resolvePath(root, field.field());
                    if (value == null || value.isMissingNode() || value.isNull()) {
                        return null;
                    }
                    return value.isTextual() ? value.asText() : value.toString();
                })
                .orElse(null);
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

    public record ExecutionResult(boolean handled, String message, ResponseMode mode, java.util.List<String> buttons,
                                  java.util.List<String> options, java.util.List<String> contextValues,
                                  boolean objectContextEnabled, String objectContextLabel) {
        public static ExecutionResult handled(String message) {
            return new ExecutionResult(true, message, ResponseMode.TEXT, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), java.util.Collections.emptyList(), false, null);
        }

        public static ExecutionResult handled(String message, ResponseMode mode, java.util.List<String> buttons,
                java.util.List<String> options, java.util.List<String> contextValues, boolean objectContextEnabled,
                String objectContextLabel) {
            return new ExecutionResult(true, message, mode,
                    buttons == null ? java.util.Collections.emptyList() : buttons,
                    options == null ? java.util.Collections.emptyList() : options,
                    contextValues == null ? java.util.Collections.emptyList() : contextValues,
                    objectContextEnabled, objectContextLabel);
        }

        public static ExecutionResult notHandled() {
            return new ExecutionResult(false, null, ResponseMode.TEXT, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), java.util.Collections.emptyList(), false, null);
        }
    }

    private record JsonBody(JsonNode node, String prettyBody) { }

    private record RenderResult(String text, java.util.List<String> buttons, java.util.List<String> options,
                                java.util.List<String> contextValues) { }
}
