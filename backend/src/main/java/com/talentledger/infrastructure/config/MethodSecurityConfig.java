package com.talentledger.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables {@code @PreAuthorize}/{@code @PostAuthorize} method-level security
 * (used by {@code AdminController}'s {@code hasRole('ADMIN')} check).
 *
 * <p>Deliberately split out from {@link SecurityConfig}, which is disabled
 * under the "local" profile: method security is unrelated to the HTTP filter
 * chain / session policy configured there, and must be active in every
 * profile. Previously {@code @EnableMethodSecurity} lived on {@code
 * SecurityConfig} itself, which meant {@code @PreAuthorize} silently did
 * nothing at all while running locally — any authenticated user (including
 * the local dev bypass) could call admin-only endpoints with no check
 * whatsoever. Combined with {@code SessionAuthFilter} now granting
 * authorities from the user's real DB role (rather than a hardcoded
 * ROLE_USER for everyone), this makes {@code hasRole('ADMIN')} actually mean
 * something in every profile.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {
}
