package com.talentledger.application.service;

import com.talentledger.domain.auth.AuthProviderPort;
import com.talentledger.domain.auth.Credentials;
import com.talentledger.domain.auth.Session;
import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserQuotaRepository;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.application.port.outbound.EmailSenderPort;
import com.talentledger.infrastructure.persistence.repository.JpaEmailVerificationTokenRepository;
import com.talentledger.infrastructure.persistence.repository.JpaPasswordResetTokenRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserRepository;
import com.talentledger.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the login security paths added/fixed this session:
 * real session tokens, banned/suspended/locked rejection, failed-attempt
 * tracking, and MFA enforcement. These are the highest-risk, most-recently
 * -changed paths in the backend, so they get covered first.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JpaUserRepository jpaUserRepository;
    @Mock private UserQuotaRepository userQuotaRepository;
    @Mock private AuthProviderPort authProviderPort;
    @Mock private EmailSenderPort emailSenderPort;
    @Mock private JpaEmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private JpaPasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private MfaService mfaService;
    @Mock private com.talentledger.infrastructure.security.clerk.ClerkTokenVerifier clerkTokenVerifier;
    @Mock private com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository jpaDataDumpRepository;
    @Mock private com.talentledger.infrastructure.persistence.repository.ContactJpaRepository contactJpaRepository;

    private AuthService authService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static final String RAW_PASSWORD = "correct-horse-battery-staple";

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, jpaUserRepository, userQuotaRepository,
                authProviderPort, emailSenderPort,
                emailVerificationTokenRepository, passwordResetTokenRepository,
                mfaService, clerkTokenVerifier,
                jpaDataDumpRepository, contactJpaRepository
        );
    }

    private User activeUser() {
        return User.create("jane@example.com", b -> b.name("Jane").passwordHash(encoder.encode(RAW_PASSWORD)));
    }

    private void stubSessionCreation() {
        when(authProviderPort.createSession(any(), any(), any()))
                .thenAnswer(inv -> Session.create(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void login_succeedsWithCorrectPassword() {
        User user = activeUser();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        stubSessionCreation();

        var response = authService.login(Credentials.of("jane@example.com", RAW_PASSWORD), "127.0.0.1", "test-agent");

        assertThat(response.sessionToken()).isNotBlank();
        assertThat(response.userId()).isEqualTo(user.getId());
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    void login_rejectsWrongPassword_andRecordsFailedAttempt() {
        User user = activeUser();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                authService.login(Credentials.of("jane@example.com", "totally-wrong-password"), "127.0.0.1", "test-agent")
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
        verify(authProviderPort, never()).createSession(any(), any(), any());
    }

    @Test
    void login_rejectsBannedUser_withoutCheckingPassword() {
        User user = activeUser();
        user.ban("policy violation");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                authService.login(Credentials.of("jane@example.com", RAW_PASSWORD), "127.0.0.1", "test-agent")
        ).isInstanceOf(UnauthorizedException.class)
         .hasMessageContaining("no longer able to sign in");

        verify(authProviderPort, never()).createSession(any(), any(), any());
    }

    @Test
    void login_rejectsLockedAccount() {
        User user = activeUser();
        // Simulate lockout by recording enough failed attempts to trip it.
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin();
        }
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                authService.login(Credentials.of("jane@example.com", RAW_PASSWORD), "127.0.0.1", "test-agent")
        ).isInstanceOf(UnauthorizedException.class)
         .hasMessageContaining("Too many failed attempts");
    }

    @Test
    void login_requiresMfaCode_whenFullySetUp() {
        User user = activeUser();
        user.enableMfa("TOTP", "encrypted-secret-value");
        user.completeMfaSetup(10);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                authService.login(Credentials.of("jane@example.com", RAW_PASSWORD), "127.0.0.1", "test-agent")
        ).isInstanceOf(UnauthorizedException.class)
         .hasMessageContaining("two-factor");

        verify(mfaService, never()).verifyMfaCode(any(), any());
    }

    @Test
    void login_doesNotRequireMfa_whenSetupStartedButNeverCompleted() {
        // enableMfa() flips mfaEnabled immediately but mfaSetupCompletedAt stays
        // null until confirmed — login must not lock these users out.
        User user = activeUser();
        user.enableMfa("TOTP", "encrypted-secret-value");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        stubSessionCreation();

        var response = authService.login(Credentials.of("jane@example.com", RAW_PASSWORD), "127.0.0.1", "test-agent");

        assertThat(response.sessionToken()).isNotBlank();
    }

    @Test
    void login_rejectsWrongMfaCode_andRecordsFailedAttempt() {
        User user = activeUser();
        user.enableMfa("TOTP", "encrypted-secret-value");
        user.completeMfaSetup(10);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(mfaService.verifyMfaCode(eq(user), eq("000000"))).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(Credentials.of("jane@example.com", RAW_PASSWORD, "000000"), "127.0.0.1", "test-agent")
        ).isInstanceOf(UnauthorizedException.class)
         .hasMessageContaining("Invalid two-factor");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void login_succeedsWithCorrectMfaCode() {
        User user = activeUser();
        user.enableMfa("TOTP", "encrypted-secret-value");
        user.completeMfaSetup(10);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(mfaService.verifyMfaCode(eq(user), eq("123456"))).thenReturn(true);
        stubSessionCreation();

        var response = authService.login(Credentials.of("jane@example.com", RAW_PASSWORD, "123456"), "127.0.0.1", "test-agent");

        assertThat(response.sessionToken()).isNotBlank();
    }

    @Test
    void login_rejectsUnknownEmail_withSameErrorAsWrongPassword() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        // Same exception type/message pattern as a wrong password, per
        // section 8.4 — must not leak whether the email exists.
        assertThatThrownBy(() ->
                authService.login(Credentials.of("ghost@example.com", RAW_PASSWORD), "127.0.0.1", "test-agent")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("Invalid credentials");
    }
}
