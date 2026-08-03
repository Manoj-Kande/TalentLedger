package com.talentledger.infrastructure.security.clerk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies Clerk-issued JWTs against Clerk's published JWKS.
 *
 * <p>Replaces the previous {@code exchangeClerkToken} stub, which accepted
 * any input and returned a fabricated success response with a random user
 * id — this actually validates the token's signature (RS256, against
 * Clerk's rotating public keys, cached by {@link NimbusJwtDecoder}),
 * expiry, and issuer before any claim is trusted.
 */
@Component
public class ClerkTokenVerifier {

    private final JwtDecoder jwtDecoder;
    private final boolean configured;

    public ClerkTokenVerifier(
            @Value("${talentledger.clerk.jwks-url:}") String jwksUrl,
            @Value("${talentledger.clerk.issuer:}") String issuer) {

        this.configured = jwksUrl != null && !jwksUrl.isBlank();

        if (configured) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUrl).build();
            if (issuer != null && !issuer.isBlank()) {
                decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            } else {
                decoder.setJwtValidator(JwtValidators.createDefault());
            }
            this.jwtDecoder = decoder;
        } else {
            this.jwtDecoder = null;
        }
    }

    /** @return true if CLERK_JWKS_URL is actually configured. */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * Verify and decode a Clerk JWT.
     *
     * @param token the raw JWT presented by the frontend after a Clerk sign-in
     * @return the decoded, signature-and-expiry-verified token
     * @throws org.springframework.security.oauth2.jwt.JwtException if the
     *         signature, expiry, or issuer don't check out
     * @throws IllegalStateException if Clerk isn't configured at all
     */
    public Jwt verify(String token) {
        if (!configured) {
            throw new IllegalStateException("Clerk is not configured (CLERK_JWKS_URL is unset)");
        }
        return jwtDecoder.decode(token);
    }
}
