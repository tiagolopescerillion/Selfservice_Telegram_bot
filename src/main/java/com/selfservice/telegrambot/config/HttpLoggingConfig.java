package com.selfservice.telegrambot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class HttpLoggingConfig {

    @Value("${app.http-logging.enabled:true}") // flip in YAML if you want
    private boolean httpLoggingEnabled;
    // inside HttpLoggingConfig.java

    @Bean(name = "loggingRestTemplate")
    public RestTemplate loggingRestTemplate() {
        // base factory with disabled redirects + timeouts
        SimpleClientHttpRequestFactory base = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection con, String method) throws IOException {
                // CRITICAL: don’t auto-follow redirects; we want to see 30x
                con.setInstanceFollowRedirects(false);
                super.prepareConnection(con, method);
            }
        };
        base.setConnectTimeout(15_000);
        base.setReadTimeout(30_000);

        // Buffering so we can log bodies
        RestTemplate rt = new RestTemplate(new BufferingClientHttpRequestFactory(base));
        if (httpLoggingEnabled) {
            rt.setInterceptors(List.of(new LoggingInterceptor()));
        }
        return rt;
    }






    static class LoggingInterceptor implements ClientHttpRequestInterceptor {
        private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {

            // ---- Request ----
            String reqBody = safe(body);
            log.info("HTTP >> {} {}\nHeaders: {}\nBody: {}",
                    request.getMethod(), request.getURI(), request.getHeaders(), truncate(reqBody, 4000));

            // Execute
            ClientHttpResponse response = execution.execute(request, body);

            // ---- Response ----
            byte[] respBytes = StreamUtils.copyToByteArray(response.getBody());
            String respBody = new String(respBytes, StandardCharsets.UTF_8);
            log.info("HTTP << {} {} {}\nHeaders: {}\nBody: {}",
                    request.getMethod(), request.getURI(), response.getStatusCode(),
                    response.getHeaders(), truncate(respBody, 4000));

            // Re-wrap so caller can read the body normally
            return new BufferingClientHttpResponseWrapper(response, respBytes);
        }

        private static String safe(byte[] b) {
            if (b == null || b.length == 0)
                return "<empty>";
            return new String(b, StandardCharsets.UTF_8);
        }

        private static String truncate(String s, int max) {
            if (s == null)
                return "<null>";
            return s.length() > max ? s.substring(0, max) + " …(truncated)" : s;
        }
    }

    /**
     * Wraps the original response but returns our buffered byte[] for any
     * subsequent reads.
     */
    static class BufferingClientHttpResponseWrapper implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        BufferingClientHttpResponseWrapper(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            // In Boot 3+, getStatusCode returns HttpStatusCode
            return delegate.getStatusCode();
        }

        @Override
        public int getRawStatusCode() throws IOException {
            return delegate.getRawStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public java.io.InputStream getBody() {
            return new java.io.ByteArrayInputStream(body);
        }
    }

}
