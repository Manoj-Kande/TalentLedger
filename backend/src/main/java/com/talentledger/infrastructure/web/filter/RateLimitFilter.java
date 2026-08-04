package com.talentledger.infrastructure.web.filter;

import com.talentledger.shared.exception.RateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.local.LocalBucketBuilder;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter — Layer 3 of the 10-layer security filter chain.
 * Uses Bucket4j with in-memory buckets.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    
    private static final int FREE_REQUESTS_PER_MINUTE = 60;
    private static final int AUTH_REGISTER_MAX = 50;
    private static final int AUTH_REGISTER_WINDOW_HOURS = 1;

    @Value("${talentledger.rate-limit.auth-login.max-attempts:5}")
    private int authLoginMax;

    @Value("${talentledger.rate-limit.auth-login.window-minutes:15}")
    private int authLoginWindowMinutes;

    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authIpBuckets = new ConcurrentHashMap<>();

    /**
     * Only trust {@code X-Forwarded-For} when explicitly running behind a
     * known reverse proxy that overwrites/strips this header on the way in.
     * Left unset (the safe default), any caller could set this header
     * themselves to spoof a different IP on every request and bypass rate
     * limiting entirely — including login brute-force protection.
     */
    @Value("${app.security.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        String clientIp = getClientIp(httpRequest);

        try {
            if (path.contains("/auth/login")) {
                long remaining = checkAuthLimitWithRemaining("login:" + clientIp, authLoginMax, Duration.ofMinutes(authLoginWindowMinutes));
                httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(authLoginMax));
                httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                httpResponse.setHeader("X-RateLimit-Window", String.valueOf(authLoginWindowMinutes * 60));
            } else if (path.contains("/auth/register")) {
                long remaining = checkAuthLimitWithRemaining("register:" + clientIp, AUTH_REGISTER_MAX, Duration.ofHours(AUTH_REGISTER_WINDOW_HOURS));
                httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(AUTH_REGISTER_MAX));
                httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                httpResponse.setHeader("X-RateLimit-Window", "3600");
            } else if (path.contains("/auth/guest")) {
                // Creates a real User row per call (item #1) — same abuse
                // surface as register, so hold it to the same limit.
                long remaining = checkAuthLimitWithRemaining("guest:" + clientIp, AUTH_REGISTER_MAX, Duration.ofHours(AUTH_REGISTER_WINDOW_HOURS));
                httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(AUTH_REGISTER_MAX));
                httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                httpResponse.setHeader("X-RateLimit-Window", "3600");
            } else if (path.contains("/auth/password-reset")) {
                long remaining = checkAuthLimitWithRemaining("reset:" + clientIp, 50, Duration.ofHours(1));
                httpResponse.setHeader("X-RateLimit-Limit", "50");
                httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                httpResponse.setHeader("X-RateLimit-Window", "3600");
            } else {
                long remaining = checkGeneralLimitWithRemaining(clientIp);
                httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(FREE_REQUESTS_PER_MINUTE));
                httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                httpResponse.setHeader("X-RateLimit-Window", "60");
            }

            chain.doFilter(request, response);
        } catch (RateLimitException e) {
            httpResponse.setStatus(429);
            httpResponse.setHeader("X-RateLimit-Remaining", "0");
            httpResponse.setHeader("Retry-After", "60");
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Try again later.\"}}");
        }
    }

    private long checkAuthLimitWithRemaining(String key, int maxRequests, Duration window) {
        Bucket bucket = authIpBuckets.computeIfAbsent(key, k -> createBucket(maxRequests, window));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitException("Too many attempts. Try again later.");
        }
        return probe.getRemainingTokens();
    }

    private long checkGeneralLimitWithRemaining(String ip) {
        Bucket bucket = ipBuckets.computeIfAbsent(ip, k ->
                createBucket(FREE_REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitException("Rate limit exceeded.");
        }
        return probe.getRemainingTokens();
    }

    private Bucket createBucket(int capacity, Duration refillDuration) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillDuration)
                .build();
        return new LocalBucketBuilder()
                .addLimit(limit)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
