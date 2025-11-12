package com.selfservice.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OAuthLoginService {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginService.class);

    private final RestTemplate rest;
    private final String authEndpoint;
    private final String tokenEndpoint;
    private final String logoutEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final PkceStore pkceStore;

    public OAuthLoginService(
            @Value("${keycloak.auth-endpoint}") String authEndpoint,
            @Value("${keycloak.token-endpoint2}") String tokenEndpoint,
            @Value("${keycloak.oauth.client-id}") String clientId,
            @Value("${keycloak.oauth.client-secret}") String clientSecret,
            @Value("${keycloak.oauth.redirect-uri}") String redirectUri,
            @Value("${keycloak.allow-insecure-certs:false}") boolean allowInsecure,
            @Value("${keycloak.logout-endpoint:}") String logoutEndpoint,
            PkceStore pkceStore
    ) {
        this.authEndpoint = Objects.requireNonNull(authEndpoint);
        this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint);
        this.clientId = Objects.requireNonNull(clientId);
        this.clientSecret = Objects.requireNonNull(clientSecret);
        this.redirectUri = Objects.requireNonNull(redirectUri);
        this.logoutEndpoint = (logoutEndpoint == null || logoutEndpoint.isBlank()) ? null : logoutEndpoint;
        this.pkceStore = pkceStore;
        this.rest = new RestTemplate(); // if you need trust-all, copy your KC RestTemplate here
        log.info("OAuthLoginService ready, redirectUri={}", redirectUri);
    }

    /** Build the browser URL for Keycloak login (with PKCE). State carries chatId and nonce. */
    public String buildAuthUrl(long chatId) {
        // PKCE: code_verifier (43-128 chars allowed); we use 64 URL-safe chars
        String codeVerifier = randomUrlSafe(64);
        String codeChallenge = s256(codeVerifier);

        String nonce = UUID.randomUUID().toString();
        pkceStore.put(nonce, codeVerifier);

        String state = chatId + ":" + nonce;
        String scope = url("openid profile email");

        String url = authEndpoint +
                "?response_type=code" +
                "&client_id=" + url(clientId) +
                "&redirect_uri=" + url(redirectUri) +
                "&scope=" + scope +
                "&state=" + url(state) +
                "&code_challenge_method=S256" +
                "&code_challenge=" + url(codeChallenge);

        log.info("Auth URL generated for chatId={} nonce={} at {}", chatId, nonce, Instant.now());
        return url;
    }

    /** Exchange the authorization code for tokens (with PKCE code_verifier). */
    public Map exchangeCodeForTokens(String code, String state) {
        String nonce = parseNonceFromState(state);
        String verifier = pkceStore.take(nonce); // one-time use
        if (verifier == null) throw new RuntimeException("PKCE verifier missing/expired");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code_verifier", verifier);

        try {
            ResponseEntity<Map> resp = rest.postForEntity(tokenEndpoint, new HttpEntity<>(form, headers), Map.class);
            return resp.getBody();
        } catch (HttpStatusCodeException ex) {
            throw new RuntimeException("Token HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
        }
    }

    public void logout(String refreshToken) {
        if (logoutEndpoint == null) {
            log.warn("Logout endpoint not configured; skipping Keycloak logout");
            return;
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            log.info("No refresh token available; skipping Keycloak logout call");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);

        try {
            ResponseEntity<String> resp = rest.postForEntity(logoutEndpoint, new HttpEntity<>(form, headers), String.class);
            log.info("Keycloak logout status={} for endpoint {}", resp.getStatusCode().value(), logoutEndpoint);
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            log.warn("Keycloak logout failed: status={} body={}", ex.getStatusCode().value(), body);
            throw new RuntimeException("Logout HTTP " + ex.getStatusCode().value() + ": " + body, ex);
        }
    }

    public String summarizeTokens(Map body) {
        if (body == null) return "No token body.";
        Object at = body.get("access_token");
        Object it = body.get("id_token");
        Object exp = body.get("expires_in");

        StringBuilder sb = new StringBuilder();
        if (at instanceof String a) {
            sb.append("access_token: prefix=").append(prefix(a))
              .append(" exp_in=").append(exp instanceof Number ? ((Number) exp).longValue() : -1).append("s\n");
            sb.append(jwtInfo("access", a));
        }
        if (it instanceof String i) {
            sb.append("id_token: prefix=").append(prefix(i)).append("\n");
            sb.append(jwtInfo("id", i));
        }
        return sb.toString();
    }

    public long parseChatIdFromState(String state) {
        if (state == null) return -1;
        int idx = state.indexOf(':');
        String first = (idx >= 0) ? state.substring(0, idx) : state;
        try { return Long.parseLong(first); } catch (Exception e) { return -1; }
    }
    private String parseNonceFromState(String state) {
        if (state == null) return null;
        int idx = state.indexOf(':');
        return (idx >= 0 && idx + 1 < state.length()) ? state.substring(idx + 1) : null;
    }

    private static String url(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String prefix(String s) { return s.length() > 12 ? s.substring(0, 12) + "..." : s; }
    private static String truncate(String s, int max) { return s.length() > max ? s.substring(0, max) + " …(truncated)" : s; }

    private static String jwtInfo(String label, String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return label + ": <invalid-jwt>\n";
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return label + "_payload: " + truncate(payloadJson, 900) + "\n";
        } catch (Exception e) {
            return label + "_payload: <decode-error: " + e.getMessage() + ">\n";
        }
    }

    private static String randomUrlSafe(int len) {
        byte[] buf = new byte[len];
        new SecureRandom().nextBytes(buf);
        // base64url without padding, then trim/replace to length window
        String s = Base64.getUrlEncoder().withoutPadding().encodeToString(buf).replace("=", "");
        return s.length() > len ? s.substring(0, len) : s;
    }
    private static String s256(String verifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
