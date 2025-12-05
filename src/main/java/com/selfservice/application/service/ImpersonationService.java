package com.selfservice.application.service;

import com.selfservice.application.config.ApimanEndpointsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class ImpersonationService {

    private static final Logger log = LoggerFactory.getLogger(ImpersonationService.class);

    private final CommonApiService commonApiService;
    private final ApimanEndpointsProperties apimanEndpointsProperties;

    public ImpersonationService(CommonApiService commonApiService,
            ApimanEndpointsProperties apimanEndpointsProperties) {
        this.commonApiService = commonApiService;
        this.apimanEndpointsProperties = apimanEndpointsProperties;
    }

    public String initiate(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }

        String url = apimanEndpointsProperties.getImpersonationInitiateUrl();
        HttpMethod method = apimanEndpointsProperties.getImpersonationInitiateMethod();

        if (!StringUtils.hasText(url)) {
            log.debug("Impersonation initiate URL is not configured; skipping exchange id lookup");
            return null;
        }

        CommonApiService.ApiResponse response = commonApiService.execute(
                new CommonApiService.ApiRequest(url, method, accessToken, Map.of(), null, null));

        if (!response.success() || response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Impersonation initiate failed with status {}", response.statusCode());
            return null;
        }

        HttpHeaders headers = response.headers();
        if (headers == null) {
            return null;
        }

        String exchangeId = headers.getFirst("X-Exchange-Id");
        if (!StringUtils.hasText(exchangeId)) {
            exchangeId = headers.getFirst("x-exchange-id");
        }

        if (!StringUtils.hasText(exchangeId)) {
            log.warn("Impersonation initiate succeeded but did not return X-Exchange-Id header");
            return null;
        }

        return exchangeId.trim();
    }
}

