package com.talentledger.infrastructure.web.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * CORS Filter — Layer 1 of the 10-layer security filter chain.
 * Strict origin allowlist, NO wildcards.
 */
@Component("talentLedgerCorsFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {

    private final Set<String> allowedOrigins;
    private final String allowedMethods;
    private final String allowedHeaders;
    private final long maxAge;

    public CorsFilter(
            @Value("${talentledger.cors.allowed-origins:http://localhost:3000}") String origins,
            @Value("${talentledger.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}") String methods,
            @Value("${talentledger.cors.allowed-headers:*}") String headers,
            @Value("${talentledger.cors.max-age:3600}") long maxAge) {
        this.allowedOrigins = Set.of(origins.split(","));
        this.allowedMethods = methods;
        this.allowedHeaders = headers;
        this.maxAge = maxAge;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String origin = httpRequest.getHeader("Origin");

        // Only set CORS headers for allowed origins
        if (origin != null && allowedOrigins.contains(origin)) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin);
            httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
            httpResponse.setHeader("Access-Control-Allow-Methods", allowedMethods);
            httpResponse.setHeader("Access-Control-Allow-Headers", allowedHeaders);
            httpResponse.setHeader("Access-Control-Max-Age", String.valueOf(maxAge));
            httpResponse.setHeader("Vary", "Origin");
        }

        // Handle preflight
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        chain.doFilter(request, response);
    }
}
