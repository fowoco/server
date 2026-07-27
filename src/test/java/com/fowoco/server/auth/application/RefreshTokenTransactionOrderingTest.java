package com.fowoco.server.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fowoco.server.auth.application.port.AccessTokenIssuer;
import com.fowoco.server.auth.application.port.AuthAuditPort;
import com.fowoco.server.auth.application.port.AuthTenantBootstrap;
import com.fowoco.server.auth.application.port.RefreshTokenGenerator;
import com.fowoco.server.auth.application.port.RefreshTokenRepository;
import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.company.application.CompanyAuthenticationReader;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RefreshTokenTransactionOrderingTest {

    private static final String TOKEN_HASH = "a".repeat(64);
    private static final UUID COMPANY_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Test
    void rotationSamplesTimeOnlyAfterTheFamilyLookupAndLock() {
        AtomicBoolean familyLookupCompleted = new AtomicBoolean();
        RefreshTokenRepository repository = repositoryThatCompletes(familyLookupCompleted);
        Clock clock = clockThatRequiresCompletedLookup(familyLookupCompleted);
        AuthAuditPort auditPort = event -> { };
        AuthTenantBootstrap tenantBootstrap = tenantBootstrap();
        RefreshTokenRotationTransaction transaction = new RefreshTokenRotationTransaction(
                repository,
                tenantBootstrap,
                mock(TenantDatabaseContext.class),
                mock(UserAccountRepository.class),
                mock(CompanyAuthenticationReader.class),
                mock(AccessTokenIssuer.class),
                mock(RefreshTokenGenerator.class),
                auditPort,
                mock(UuidGenerator.class),
                clock
        );

        RefreshOutcome outcome = transaction.rotate(TOKEN_HASH);

        assertThat(outcome.status()).isEqualTo(RefreshOutcome.Status.INVALID);
    }

    @Test
    void logoutSamplesTimeOnlyAfterTheFamilyLookupAndLock() {
        AtomicBoolean familyLookupCompleted = new AtomicBoolean();
        RefreshTokenRepository repository = repositoryThatCompletes(familyLookupCompleted);
        Clock clock = clockThatRequiresCompletedLookup(familyLookupCompleted);
        AuthAuditPort auditPort = event -> { };
        AuthTenantBootstrap tenantBootstrap = tenantBootstrap();
        RefreshTokenLogoutTransaction transaction = new RefreshTokenLogoutTransaction(
                repository,
                tenantBootstrap,
                mock(TenantDatabaseContext.class),
                auditPort,
                clock
        );

        transaction.revokeIfKnown(TOKEN_HASH);

        assertThat(familyLookupCompleted).isTrue();
    }

    private RefreshTokenRepository repositoryThatCompletes(AtomicBoolean familyLookupCompleted) {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        when(repository.findByTokenHashWithFamilyLock(TOKEN_HASH)).thenAnswer(invocation -> {
            familyLookupCompleted.set(true);
            return Optional.empty();
        });
        return repository;
    }

    private AuthTenantBootstrap tenantBootstrap() {
        AuthTenantBootstrap bootstrap = mock(AuthTenantBootstrap.class);
        when(bootstrap.findCompanyIdByRefreshTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(COMPANY_ID));
        return bootstrap;
    }

    private Clock clockThatRequiresCompletedLookup(AtomicBoolean familyLookupCompleted) {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> {
            assertThat(familyLookupCompleted)
                    .as("current time must be sampled after the token family lookup and lock")
                    .isTrue();
            return NOW;
        });
        return clock;
    }
}
