package com.selfservice.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Service
public class CommonApiService {

    private static final Logger log = LoggerFactory.getLogger(CommonApiService.class);

    private final RestTemplate restTemplate;

    public CommonApiService(@Qualifier("loggingRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ApiResponse execute(ApiRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return new ApiResponse(false, 0, new HttpHeaders(), null,
                    "Endpoint URL is not configured.");
        }

        String targetUrl;
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(request.url());
            if (request.queryParams() != null) {
                request.queryParams().forEach(builder::queryParam);
            }
            targetUrl = builder.build(true).toUriString();
        } catch (IllegalArgumentException ex) {
            log.error("Invalid API URL configured: {}", request.url(), ex);
            return new ApiResponse(false, 0, new HttpHeaders(), null, "Invalid endpoint URL.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "SelfserviceTelegramBot/1.0");
        if (request.bearerToken() != null && !request.bearerToken().isBlank()) {
            headers.setBearerAuth(request.bearerToken());
        }
        if (request.additionalHeaders() != null) {
            headers.putAll(request.additionalHeaders());
        }

        HttpEntity<?> entity = request.body() == null ? new HttpEntity<>(headers)
                : new HttpEntity<>(request.body(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(targetUrl, request.method(), entity, String.class);
            return new ApiResponse(true, response.getStatusCode().value(), response.getHeaders(), response.getBody(), null);
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            HttpHeaders errorHeaders = ex.getResponseHeaders() == null ? new HttpHeaders() : ex.getResponseHeaders();
            return new ApiResponse(false, ex.getStatusCode().value(), errorHeaders, body,
                    ex.getStatusCode().toString());
        } catch (Exception ex) {
            log.error("API call failed", ex);
            return new ApiResponse(false, 0, new HttpHeaders(), null,
                    ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "<no-message>" : ex.getMessage()));
        }
    }

    public record ApiRequest(String url, HttpMethod method, String bearerToken, Map<String, ?> queryParams,
                             HttpHeaders additionalHeaders, Object body) { }

    public record ApiResponse(boolean success, int statusCode, HttpHeaders headers, String body, String errorMessage) {
        public boolean isJsonResponse() {
            MediaType contentType = headers == null ? null : headers.getContentType();
            return contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType);
        }
    }
}

