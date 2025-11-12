package com.selfservice.application.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;


@Service
public class KeycloakAuthService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthService.class);

    private final RestTemplate rest;
    private final String tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final boolean allowInsecure;

    // cached token + expiry
    private volatile String cachedToken = null;
    private volatile long expiryEpochMs = 0L; // epoch millis

    public KeycloakAuthService(
            @Value("${keycloak.token-endpoint}") String tokenEndpoint,
            @Value("${keycloak.client-credentials.client-id}") String clientId,
            @Value("${keycloak.client-credentials.client-secret}") String clientSecret,
            @Value("${keycloak.allow-insecure-certs:false}") boolean allowInsecure) {

        this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint, "keycloak.token-endpoint is required");
        this.clientId = Objects.requireNonNull(clientId, "keycloak.client-id is required");
        this.clientSecret = Objects.requireNonNull(clientSecret, "keycloak.client-secret is required");
        this.allowInsecure = allowInsecure;

        this.rest = buildRestTemplate(allowInsecure);
        log.info("KeycloakAuthService configured endpoint={} insecure={}", this.tokenEndpoint, this.allowInsecure);
    }

    private RestTemplate buildRestTemplate(boolean insecure) {
        if (!insecure)
            return new RestTemplate();

        try {
            // create an SSLContext that trusts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // Apply to HttpsURLConnection so the default HTTP client used by RestTemplate
            // (SimpleClientHttpRequestFactory) will accept insecure certs.
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            return new RestTemplate();
        } catch (Exception ex) {
            log.warn("Failed to init insecure SSL RestTemplate, falling back to default", ex);
            return new RestTemplate();
        }
    }

    /** Return a valid token (cached, with 30s safety buffer). */
    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && expiryEpochMs > (now + 30_000)) {
            return cachedToken;
        }
        fetchToken(); // refresh
        return cachedToken;
    }

    /** Fetch a fresh token from Keycloak (client_credentials). */
    private void fetchToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        try {
            ResponseEntity<Map> resp = rest.postForEntity(tokenEndpoint, new HttpEntity<>(form, headers), Map.class);

            int status = resp.getStatusCode().value();
            Map<?, ?> body = resp.getBody();
            if (status >= 200 && status < 300 && body != null) {
                Object token = body.get("access_token");
                Object expires = body.get("expires_in");
                if (token instanceof String) {
                    cachedToken = (String) token;
                    long seconds = (expires instanceof Number) ? ((Number) expires).longValue() : 300L;
                    expiryEpochMs = System.currentTimeMillis() + (seconds * 1000L);
                    log.info("Keycloak token OK ({}), expires at {}", status, Instant.ofEpochMilli(expiryEpochMs));
                } else {
                    throw new IllegalStateException("No access_token in response");
                }
            } else {
                throw new IllegalStateException("Token HTTP " + status + " body=" + body);
            }
        } catch (HttpStatusCodeException ex) {
            String err = ex.getResponseBodyAsString();
            log.error("Keycloak token HTTP {} -> {}", ex.getStatusCode().value(), err);
            throw ex;
        }
    }

    /**
     * For Telegram: attempt auth and return a terse report string (status + body or
     * error).
     */
    public String authenticateAndReport() {
        try {
            String token = getAccessToken();
            String prefix = (token == null || token.length() < 10) ? token : token.substring(0, 10) + "...";
            long secondsLeft = Math.max(0, (expiryEpochMs - System.currentTimeMillis()) / 1000);
            return "Auth OK: status=200; token_prefix=" + prefix + "; expires_in=" + secondsLeft + "s";
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            return "Auth ERROR: status=" + ex.getStatusCode().value() + "; body=" + (body == null ? "<no-body>" : body);
        } catch (Exception ex) {
            return "Auth ERROR: " + ex.getClass().getSimpleName() + ": "
                    + (ex.getMessage() == null ? "<no-message>" : ex.getMessage());
        }
    }
}
