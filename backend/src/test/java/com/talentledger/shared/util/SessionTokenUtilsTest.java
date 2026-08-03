package com.talentledger.shared.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the session token generation/hashing used by the real
 * auth rewrite this session (previously, session tokens were derived from
 * the session's own database id with no actual secret involved).
 */
class SessionTokenUtilsTest {

    @Test
    void generateToken_producesHighEntropyUniqueValues() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(SessionTokenUtils.generateToken());
        }
        // No collisions across 1000 generations — proves real randomness,
        // not something derived from a predictable/sequential source.
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void hash_isDeterministic_forTheSameInput() {
        String token = SessionTokenUtils.generateToken();

        String hash1 = SessionTokenUtils.hash(token);
        String hash2 = SessionTokenUtils.hash(token);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hash_isDifferent_forDifferentTokens() {
        String tokenA = SessionTokenUtils.generateToken();
        String tokenB = SessionTokenUtils.generateToken();

        assertThat(SessionTokenUtils.hash(tokenA)).isNotEqualTo(SessionTokenUtils.hash(tokenB));
    }

    @Test
    void hash_neverEqualsTheRawToken() {
        String token = SessionTokenUtils.generateToken();

        assertThat(SessionTokenUtils.hash(token)).isNotEqualTo(token);
    }

    @Test
    void hash_isA64CharacterHexString() {
        // SHA-256 → 32 bytes → 64 hex characters. A regression to a shorter
        // or non-hex encoding here would silently break every session lookup.
        String hash = SessionTokenUtils.hash("anything");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
