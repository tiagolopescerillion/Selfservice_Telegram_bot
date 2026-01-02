package com.selfservice.application.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper wrapped = (request instanceof ContentCachingRequestWrapper)
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request);

        String method = wrapped.getMethod();
        String path = wrapped.getRequestURI();
        String query = wrapped.getQueryString();
        if (query != null && !query.isBlank()) {
            path = path + "?" + query;
        }

        log.info("Incoming request {} {} from {}", method, path, request.getRemoteAddr());

        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            // Log body for POST/PUT/PATCH up to a limited size
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                byte[] buf = wrapped.getContentAsByteArray();
                if (buf != null && buf.length > 0) {
                    int length = Math.min(buf.length, 4096);
                    String body = new String(buf, 0, length, StandardCharsets.UTF_8).trim();
                    if (buf.length > length) body += "...[truncated]";
                    log.info("Request body ({} bytes): {}", buf.length, body);
                } else {
                    log.info("Request body: <empty>");
                }
            }
        }
    }
}
