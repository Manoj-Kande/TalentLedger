package com.talentledger.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Security Configuration — Spring Security integration with our custom filter chain.
 *
 * Our custom filters (CorsFilter -> RequestIdFilter -> RateLimitFilter ->
 * SecurityHeadersFilter -> SessionAuthFilter) run BEFORE Spring Security's chain.
 *
 * IMPORTANT: Spring Security's own SecurityContextHolderFilter reloads (and
 * otherwise overwrites) the SecurityContext at the start of its chain from a
 * SecurityContextRepository. Without an explicit repository wired here AND
 * used by SessionAuthFilter to persist what it resolves, the authentication
 * set by SessionAuthFilter gets wiped before AuthorizationFilter ever sees
 * it — producing a 403 on every authenticated request even with a valid
 * session token. RequestAttributeSecurityContextRepository fixes this by
 * reading/writing the SecurityContext via a request attribute instead of an
 * HTTP session (which we don't use — see STATELESS below).
 *
 * The HTTP-level filter chain below (stateless session policy, CSRF
 * disable, public-path allowlist) is DISABLED for "local" profile — the
 * SessionAuthFilter handles the equivalent checks directly there. Only
 * active for non-local profiles (dev, prod) when Spring Security is on
 * classpath.
 *
 * <p>{@code @EnableMethodSecurity} (which makes {@code @PreAuthorize} work)
 * deliberately lives in {@link MethodSecurityConfig} instead, unconditionally
 * — method security is independent of this HTTP filter chain and must be
 * enforced in every profile, including local, or endpoints like
 * {@code AdminController} would be completely unprotected during local
 * testing.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnClass(name = "org.springframework.security.config.annotation.web.builders.HttpSecurity")
@Profile("!local")
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new RequestAttributeSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
        http
            // Disable CSRF (REST API with opaque tokens)
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless -- no HTTP sessions
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Make Spring Security read/write the SecurityContext via a request
            // attribute (populated by SessionAuthFilter) instead of an HTTP
            // session, which we never create.
            .securityContext(context -> context.securityContextRepository(securityContextRepository))

            // Disable default form login and HTTP basic
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // Permit public endpoints without auth.
            // IMPORTANT: this must stay in sync with SessionAuthFilter.PUBLIC_PATHS.
            // Endpoints under /api/v1/auth/** that require a session (logout,
            // mfa/*, etc — see master API spec section 11.1) are deliberately
            // NOT listed here; only truly anonymous auth endpoints are.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/clerk",
                    "/api/v1/auth/exchange",
                    "/api/v1/auth/guest",
                    "/api/v1/billing/webhook",
                    "/api/v1/auth/password-reset",
                    "/api/v1/auth/password-reset/confirm",
                    "/api/v1/auth/verify-email",
                    "/api/v1/auth/resend-verification",
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/api/docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/h2-console/**"
                ).permitAll()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}