package com.talentledger.application.service;

import com.talentledger.application.port.inbound.AdminUseCase;
import com.talentledger.domain.auth.AuthProviderPort;
import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserQuotaRepository;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.domain.user.UserStatus;
import com.talentledger.infrastructure.persistence.repository.JpaAdminAuditLogRepository;
import com.talentledger.infrastructure.persistence.repository.JpaCampaignRepository;
import com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository;
import com.talentledger.infrastructure.persistence.repository.CompanyJpaRepository;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserQuotaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaSystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdminService.banUser/unbanUser.
 *
 * <p>These exist specifically to guard against a regression to the original
 * bug: the endpoints returned a 200 "Success" response with the user's
 * unchanged status, having never actually persisted a ban. An admin acting
 * on abusive accounts would believe the ban worked when it hadn't.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserQuotaRepository userQuotaRepository;
    @Mock private AuthProviderPort authProviderPort;
    @Mock private JpaAdminAuditLogRepository adminAuditLogRepository;
    @Mock private JpaSystemConfigRepository systemConfigRepository;
    @Mock private JpaUserQuotaRepository jpaUserQuotaRepository;
    @Mock private ContactJpaRepository contactJpaRepository;
    @Mock private JpaDataDumpRepository jpaDataDumpRepository;
    @Mock private CompanyJpaRepository companyJpaRepository;
    @Mock private JpaCampaignRepository jpaCampaignRepository;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, userQuotaRepository, authProviderPort,
                adminAuditLogRepository, systemConfigRepository, jpaUserQuotaRepository,
                contactJpaRepository, jpaDataDumpRepository, companyJpaRepository,
                jpaCampaignRepository);
    }

    @Test
    void banUser_actuallyPersistsTheBannedStatus() {
        User user = User.create("offender@example.com", b -> b.name("Offender"));
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var result = adminService.banUser(adminId, user.getId(), "spam");

        assertThat(result).isInstanceOf(AdminUseCase.Success.class);
        assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);

        // The critical assertion: save() must actually be called with the
        // now-banned user, not just returned in an unpersisted response.
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.BANNED);
    }

    @Test
    void banUser_revokesAllExistingSessions() {
        User user = User.create("offender@example.com", b -> b.name("Offender"));
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        adminService.banUser(adminId, user.getId(), "spam");

        verify(authProviderPort).revokeAllUserSessions(eq(user.getId()), any());
    }

    @Test
    void banUser_recordsAnAuditLogEntry() {
        User user = User.create("offender@example.com", b -> b.name("Offender"));
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        adminService.banUser(adminId, user.getId(), "spam");

        verify(adminAuditLogRepository).save(any());
    }

    @Test
    void banUser_returnsNotFound_forUnknownUser() {
        UUID missingUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(missingUserId)).thenReturn(Optional.empty());

        var result = adminService.banUser(adminId, missingUserId, "spam");

        assertThat(result).isInstanceOf(AdminUseCase.NotFound.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void unbanUser_restoresActiveStatus() {
        User user = User.create("reformed@example.com", b -> b.name("Reformed"));
        user.ban("previous violation");
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var result = adminService.unbanUser(adminId, user.getId(), "appeal approved");

        assertThat(result).isInstanceOf(AdminUseCase.Success.class);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
    }
}
