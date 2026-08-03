package com.talentledger.infrastructure.web.filter;

import com.talentledger.domain.auth.AuthProviderPort;
import com.talentledger.domain.auth.Session;
import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.domain.user.UserStatus;
import com.talentledger.shared.constants.SecurityConstants;
import com.talentledger.shared.util.SessionTokenUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Session Authentication Filter — Layer 5 of the security filter chain.
 *
 * <p>Validates the opaque session token (from {@code X-Session-Token}) by
 * hashing it and looking up the matching, non-expired, non-revoked session
 * in {@code user_sessions} via {@link AuthProviderPort}. The user id is
 * taken from the persisted {@link Session}, never parsed out of the token
 * itself — the token carries no identity information on its own.
 *
 * <p>Skipped for: auth endpoints, actuator, docs, static resources, configs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class SessionAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthFilter.class);

    @Autowired
    private Environment environment;

    @Autowired
    private AuthProviderPort authProviderPort;

    /**
     * Used to resolve the authenticated user's actual {@code role} (USER /
     * PREMIUM / ADMIN) so Spring Security authorities reflect reality.
     *
     * <p>Previously every authenticated request was hardcoded to
     * {@code ROLE_USER} regardless of the user's real role in the database —
     * meaning {@code @PreAuthorize("hasRole('ADMIN')")} on
     * {@code AdminController} could never pass for anyone, including real
     * admins, in any profile where Spring Security's method security was
     * active. Loading the real role here and granting the matching authority
     * fixes that at the source. This also lets us reject requests from
     * accounts that were banned/suspended after their session was issued,
     * as defense-in-depth alongside the explicit session revocation that
     * already happens on ban.
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * Only present when SecurityConfig (i.e. the "!local" profile) is
     * active. Spring Security's own SecurityContextHolderFilter reloads the
     * SecurityContext from this repository at the start of its chain — if
     * we only set SecurityContextHolder directly and never save into this
     * repository, that reload wipes out our authentication and every
     * protected request 403s even with a valid token.
     */
    @Autowired(required = false)
    private SecurityContextRepository securityContextRepository;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/clerk",
            "/api/v1/auth/password-reset",
            "/api/v1/auth/password-reset/confirm",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/exchange",
            "/api/v1/auth/guest",
            "/api/v1/billing/webhook",
            "/api/v1/configs",
            "/api/docs",
            "/api/docs/ui",
            "/api/docs/swagger-config",
            "/swagger-ui",
            "/swagger-ui/",
            "/actuator/health",
            "/actuator/info"
    );

    /**
     * Local-only developer convenience: lets a request through without a real
     * session IF AND ONLY IF the "local" profile is explicitly active AND the
     * caller supplies an explicit {@code X-Dev-UserId} header. Unlike the
     * previous implementation, this never falls back to a random UUID and is
     * never active just because no profile happens to be configured.
     */
    private boolean isLocalProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.asList(activeProfiles).contains("local");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        log.debug("SessionAuthFilter start: {} {}", method, path);

        // Let CORS preflight pass through without session checks.
        if ("OPTIONS".equalsIgnoreCase(method)) {
            log.debug("SessionAuthFilter preflight bypass: {} {}", method, path);
            chain.doFilter(request, response);
            return;
        }

        // Skip public endpoints
        if (isPublicPath(path)) {
            log.debug("SessionAuthFilter public bypass: {} {}", method, path);
            chain.doFilter(request, response);
            return;
        }

        String token = httpRequest.getHeader(SecurityConstants.SESSION_TOKEN_HEADER);
        log.debug("SessionAuthFilter protected path: {} {} tokenPresent={}",
                method, path, token != null && !token.isBlank());

        if (token == null || token.isBlank()) {
            String devUserId = httpRequest.getHeader("X-Dev-UserId");
            if (isLocalProfile() && devUserId != null) {
                log.debug("SessionAuthFilter local dev bypass: {} {}", method, path);
                UUID userId = UUID.fromString(devUserId);
                com.talentledger.domain.user.UserRole devRole = userRepository.findById(userId)
                        .map(User::getRole)
                        .orElse(com.talentledger.domain.user.UserRole.USER);
                authenticateRequest(httpRequest, httpResponse, userId, "dev-token", devRole);
                chain.doFilter(request, response);
                return;
            }
            log.warn("SessionAuthFilter unauthorized: {} {} reason=missing-token", method, path);
            sendUnauthorized(httpResponse, "Session token required");
            return;
        }

        String tokenHash = SessionTokenUtils.hash(token);
        Optional<Session> sessionOpt = authProviderPort.validateAndGetSession(tokenHash);

        if (sessionOpt.isEmpty()) {
            log.warn("SessionAuthFilter unauthorized: {} {} reason=invalid-or-expired-token", method, path);
            sendUnauthorized(httpResponse, "Invalid or expired session token");
            return;
        }

        Session session = sessionOpt.get();

        Optional<User> userOpt = userRepository.findById(session.getUserId());
        if (userOpt.isEmpty()) {
            log.warn("SessionAuthFilter unauthorized: {} {} reason=user-not-found userId={}", method, path, session.getUserId());
            sendUnauthorized(httpResponse, "Invalid or expired session token");
            return;
        }
        User user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("SessionAuthFilter forbidden: {} {} userId={} status={}", method, path, session.getUserId(), user.getStatus());
            sendForbidden(httpResponse, "Account is " + user.getStatus().name().toLowerCase() + ".");
            return;
        }

        log.debug("SessionAuthFilter authenticated: {} {} userId={} role={}", method, path, session.getUserId(), user.getRole());
        authenticateRequest(httpRequest, httpResponse, session.getUserId(), token, user.getRole());
        httpRequest.setAttribute("sessionId", session.getId());
        chain.doFilter(request, response);
    }

    /**
     * Set both the request attribute (read by controllers) and the Spring
     * Security context (read by {@code @PreAuthorize} / authorizeHttpRequests)
     * for the resolved, verified user id.
     */
    private void authenticateRequest(HttpServletRequest httpRequest, HttpServletResponse httpResponse,
                                      UUID userId, String token, com.talentledger.domain.user.UserRole role) {
        httpRequest.setAttribute("userId", userId);
        httpRequest.setAttribute("session_token", token);
        httpRequest.setAttribute("userRole", role.name());
        setAuthenticated(httpRequest, httpResponse, userId, token, role);
    }

    /**
     * Set Spring Security context so Spring's authorizeHttpRequests recognizes the user.
     *
     * <p>Setting {@link SecurityContextHolder} alone is not enough: Spring
     * Security's {@code SecurityContextHolderFilter}, which runs later in
     * the chain, reloads the context from {@link #securityContextRepository}
     * and overwrites whatever is in the holder. We must explicitly persist
     * into that same repository so the reload picks up this authentication
     * instead of resetting to anonymous (which otherwise manifests as a 403
     * on every protected endpoint despite a valid session token).
     */
    private void setAuthenticated(HttpServletRequest httpRequest, HttpServletResponse httpResponse,
                                   UUID userId, String token, com.talentledger.domain.user.UserRole role) {
        try {
            List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            if (role == com.talentledger.domain.user.UserRole.PREMIUM || role == com.talentledger.domain.user.UserRole.ADMIN) {
                authorities.add(new SimpleGrantedAuthority("ROLE_PREMIUM"));
            }
            if (role == com.talentledger.domain.user.UserRole.ADMIN) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userId.toString(), token, authorities);
            ((UsernamePasswordAuthenticationToken) authentication).setDetails(userId);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            if (securityContextRepository != null) {
                securityContextRepository.saveContext(context, httpRequest, httpResponse);
            }
        } catch (Exception e) {
            // Spring Security may not be on classpath (local profile) — that's OK
            log.trace("Spring Security context not set: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        // Clean up
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith)
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}}");
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\"ACCOUNT_INACTIVE\",\"message\":\"" + message + "\"}}");
    }
}