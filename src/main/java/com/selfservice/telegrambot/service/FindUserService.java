package com.selfservice.telegrambot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfservice.telegrambot.service.dto.AccountSummary;
import com.selfservice.telegrambot.service.dto.FindUserResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FindUserService {

    private static final Logger log = LoggerFactory.getLogger(FindUserService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String findUserEndpoint;

    public FindUserService(@Qualifier("loggingRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${apiman.find-user.url:https://lonlinux13.cerillion.com:49987/apiman-gateway/CSS-MASTER-ORG/findUser/1.0?offset=0&limit=1}")
            String findUserEndpoint) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.findUserEndpoint = findUserEndpoint;
    }

    public FindUserResult fetchAccountNumbers(String accessToken) {
        if (findUserEndpoint == null || findUserEndpoint.isBlank()) {
            return new FindUserResult(false, "APIMAN[FindUser] ERROR: endpoint URL is not configured.", List.of(), null);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", "SelfserviceTelegramBot/1.0");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    findUserEndpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            String body = response.getBody() == null ? "" : response.getBody();
            String contentType = response.getHeaders().getContentType() == null
                    ? "<none>"
                    : response.getHeaders().getContentType().toString();
            int bodyBytes = body.getBytes(StandardCharsets.UTF_8).length;

            String summary = "APIMAN[FindUser] "
                    + response.getStatusCode().value()
                    + " (" + contentType + ", " + bodyBytes + " bytes)";

            if (!response.getStatusCode().is2xxSuccessful()) {
                return new FindUserResult(false, summary + "\n" + (body.isBlank() ? "<empty body>" : body), List.of(), null);
            }

            if (response.getHeaders().getContentType() == null
                    || !MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType())) {
                log.warn("findUser response is not JSON (Content-Type={})", response.getHeaders().getContentType());
            }

            try {
                ParsedResponse parsed = extractAccountsAndName(body);
                return new FindUserResult(true, summary, parsed.accounts(), parsed.givenName());
            } catch (Exception parseError) {
                log.error("Unable to parse findUser response body", parseError);
                return new FindUserResult(false, summary + "\nParse error: " + parseError.getMessage(), List.of(), null);
            }
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            MediaType ct = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getContentType();
            String contentType = ct == null ? "<none>" : ct.toString();
            String summary = "APIMAN[FindUser] ERROR: status=" + ex.getStatusCode().value()
                    + " (" + contentType + ")";
            return new FindUserResult(false, summary + "\n" + (body == null ? "<no-body>" : truncate(body, 3500)), List.of(), null);
        } catch (Exception ex) {
            String summary = "APIMAN[FindUser] ERROR: " + ex.getClass().getSimpleName()
                    + ": " + (ex.getMessage() == null ? "<no-message>" : ex.getMessage());
            return new FindUserResult(false, summary, List.of(), null);
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

