package com.selfservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfservice.application.config.ApimanEndpointsProperties;
import com.selfservice.application.dto.AccountSummary;
import com.selfservice.application.dto.FindUserResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FindUserService {

    private static final Logger log = LoggerFactory.getLogger(FindUserService.class);

    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_LIMIT = 1;

    private final CommonApiService commonApiService;
    private final ApimanEndpointsProperties apimanEndpoints;
    private final ObjectMapper objectMapper;
    private final String findUserEndpoint;
    private final Map<String, String> configuredQueryParams;

    public FindUserService(CommonApiService commonApiService,
            ObjectMapper objectMapper,
            ApimanEndpointsProperties apimanEndpoints) {
        this.commonApiService = commonApiService;
        this.apimanEndpoints = apimanEndpoints;
        this.objectMapper = objectMapper;
        this.findUserEndpoint = apimanEndpoints.getFindUserUrl();
        this.configuredQueryParams = apimanEndpoints.getFindUserQueryParams();
        if (this.findUserEndpoint == null) {
            log.warn("APIMAN find-user endpoint is not configured; account discovery will be disabled.");
        }
    }

    public FindUserResult fetchAccountNumbers(String accessToken) {
        if (findUserEndpoint == null || findUserEndpoint.isBlank()) {
            return new FindUserResult(false, "APIMAN[FindUser] ERROR: endpoint URL is not configured.", List.of(), null);
        }

        Map<String, String> queryParams = new LinkedHashMap<>(configuredQueryParams);
        queryParams.putIfAbsent("offset", String.valueOf(DEFAULT_OFFSET));
        queryParams.putIfAbsent("limit", String.valueOf(DEFAULT_LIMIT));

        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(findUserEndpoint, apimanEndpoints.getFindUserMethod(), accessToken,
                        queryParams, null, null));

        if (!response.success()) {
            String body = response.body() == null ? "<empty body>" : truncate(response.body(), 3500);
            String summary = response.statusCode() == 0
                    ? "APIMAN[FindUser] ERROR: " + response.errorMessage()
                    : "APIMAN[FindUser] ERROR: status=" + response.statusCode();
            return new FindUserResult(false, summary + "\n" + body, List.of(), null);
        }

        String body = response.body() == null ? "" : response.body();
        String contentType = response.headers().getContentType() == null
                ? "<none>"
                : response.headers().getContentType().toString();
        int bodyBytes = body.getBytes(StandardCharsets.UTF_8).length;

        String summary = "APIMAN[FindUser] "
                + response.statusCode()
                + " (" + contentType + ", " + bodyBytes + " bytes)";

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new FindUserResult(false, summary + "\n" + (body.isBlank() ? "<empty body>" : body), List.of(), null);
        }

        if (response.headers().getContentType() == null
                || !MediaType.APPLICATION_JSON.isCompatibleWith(response.headers().getContentType())) {
            log.warn("findUser response is not JSON (Content-Type={})", response.headers().getContentType());
        }

        try {
            ParsedResponse parsed = extractAccountsAndName(body);
            return new FindUserResult(true, summary, parsed.accounts(), parsed.givenName());
        } catch (Exception parseError) {
            log.error("Unable to parse findUser response body", parseError);
            return new FindUserResult(false, summary + "\nParse error: " + parseError.getMessage(), List.of(), null);
        }
    }

    private ParsedResponse extractAccountsAndName(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return new ParsedResponse(List.of(), null);
        }

        JsonNode root = objectMapper.readTree(body);
        Map<String, AccountSummary> accounts = new LinkedHashMap<>();
        String preferredName = null;

        if (root.isArray()) {
            for (JsonNode individual : root) {
                preferredName = pickPreferredName(preferredName, individual);
                collectFromIndividual(individual, accounts);
            }
        } else {
            preferredName = pickPreferredName(preferredName, root);
            collectFromIndividual(root, accounts);
        }

        return new ParsedResponse(List.copyOf(accounts.values()), preferredName);
    }

    private void collectFromIndividual(JsonNode individual, Map<String, AccountSummary> accounts) {
        if (individual == null) {
            return;
        }
        JsonNode relatedParty = individual.get("relatedParty");
        if (relatedParty == null || !relatedParty.isArray()) {
            return;
        }
        for (JsonNode party : relatedParty) {
            if (party == null) {
                continue;
            }
            String referredType = party.path("@referredType").asText("");
            if (!"BillingAccountExtended".equalsIgnoreCase(referredType)) {
                continue;
            }
            String id = party.path("id").asText("").trim();
            if (id.isEmpty()) {
                continue;
            }
            String name = party.path("name").asText("").trim();
            accounts.putIfAbsent(id, new AccountSummary(id, name));
        }
    }

    private String pickPreferredName(String current, JsonNode individual) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        if (individual == null) {
            return current;
        }
        String given = individual.path("givenName").asText("").trim();
        if (!given.isEmpty()) {
            return given;
        }
        String fullName = individual.path("fullName").asText("").trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        return current;
    }

    private static String truncate(String input, int max) {
        if (input == null) {
            return "<null>";
        }
        return input.length() > max ? input.substring(0, max) + "\n…(truncated)" : input;
    }

    private record ParsedResponse(List<AccountSummary> accounts, String givenName) {
    }
}

