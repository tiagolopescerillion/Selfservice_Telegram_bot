package com.selfservice.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ExternalApiService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);

    private final CommonApiService commonApiService;
    private final String troubleTicketUrl;

    public ExternalApiService(CommonApiService commonApiService,
            @Value("${external.api.trouble-ticket.url}") String troubleTicketUrl) {
        this.commonApiService = commonApiService;
        this.troubleTicketUrl = troubleTicketUrl;
    }

    public String callTroubleTicketApi(String bearerToken) {
        if (troubleTicketUrl == null || troubleTicketUrl.isBlank()) {
            return "API ERROR: trouble-ticket endpoint is not configured.";
        }
        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(troubleTicketUrl, HttpMethod.GET, bearerToken,
                        Map.of(), null, null));

        if (!response.success()) {
            String body = response.body() == null ? "<no body>" : response.body();
            log.error("API HTTP {} -> {}", response.statusCode(), body);
            return "API ERROR: " + (response.statusCode() == 0 ? response.errorMessage()
                    : ("status=" + response.statusCode())) + "\n" + body;
        }

        log.info("External API status={} body={}", response.statusCode(), response.body());
        return "API OK (" + response.statusCode() + "):\n" + response.body();
    }
}
