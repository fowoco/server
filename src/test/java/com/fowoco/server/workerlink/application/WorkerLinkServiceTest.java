package com.fowoco.server.workerlink.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.approval.domain.ApprovalRequest;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.settings.application.port.CompanySettingsRepository;
import com.fowoco.server.settings.domain.CompanySettings;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.application.port.TaskTransitionRecorder;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.workerlink.application.port.WorkerLinkGenerator;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WorkerLinkServiceTest {

    private static final UUID TASK_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID COMPANY_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000002"
    );
    private static final UUID ACTOR_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000003"
    );
    private static final UUID WORKER_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000004"
    );
    private static final UUID WORKER_LINK_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000005"
    );
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final RequestMetadata METADATA = new RequestMetadata("request-1", "trace-1");

    @Test
    void bindsActorTenantBeforeFirstRepositoryAccess() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        ApprovalRequestRepository approvalRepository = mock(ApprovalRequestRepository.class);
        CompanySettingsRepository companySettingsRepository =
                mock(CompanySettingsRepository.class);
        WorkerLinkRepository workerLinkRepository = mock(WorkerLinkRepository.class);
        TenantDatabaseContext tenantDatabaseContext = mock(TenantDatabaseContext.class);
        when(taskRepository.findByIdAndCompanyId(TASK_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        WorkerLinkService service = new WorkerLinkService(
                taskRepository,
                approvalRepository,
                companySettingsRepository,
                workerLinkRepository,
                mock(TaskTransitionRecorder.class),
                mock(AuditEventRepository.class),
                mock(WorkerLinkGenerator.class),
                mock(WorkerLinkHasher.class),
                tenantDatabaseContext,
                mock(UuidGenerator.class),
                mock(Clock.class)
        );
        WorkerLinkIssueCommand command = new WorkerLinkIssueCommand(
                TASK_ID,
                null,
                false,
                "worker-link-issue-1"
        );
        ActorContext actor = new ActorContext(
                ACTOR_ID,
                COMPANY_ID,
                Set.of(UserRole.HR)
        );

        assertThatThrownBy(() -> service.issue(command, actor, METADATA))
                .isInstanceOf(ApiException.class);

        InOrder order = inOrder(tenantDatabaseContext, taskRepository);
        order.verify(tenantDatabaseContext).setCompanyIdForCurrentTransaction(COMPANY_ID);
        order.verify(taskRepository).findByIdAndCompanyId(TASK_ID, COMPANY_ID);
    }

    @Test
    void usesCompanySettingWhenRequestExpiryIsOmitted() {
        ServiceFixture fixture = validFixture();
        when(fixture.companySettingsRepository().findByCompanyId(COMPANY_ID))
                .thenReturn(Optional.of(companySettings(24L)));

        WorkerLinkIssueResult result = fixture.service().issue(command(null), actor(), METADATA);

        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(24L * 60L * 60L));
        verify(fixture.companySettingsRepository()).findByCompanyId(COMPANY_ID);
    }

    @Test
    void explicitRequestExpiryTakesPrecedenceWithoutReadingCompanySetting() {
        ServiceFixture fixture = validFixture();

        WorkerLinkIssueResult result = fixture.service().issue(command(12L), actor(), METADATA);

        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(12L * 60L * 60L));
        verifyNoInteractions(fixture.companySettingsRepository());
    }

    private ServiceFixture validFixture() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        ApprovalRequestRepository approvalRepository = mock(ApprovalRequestRepository.class);
        CompanySettingsRepository companySettingsRepository =
                mock(CompanySettingsRepository.class);
        WorkerLinkRepository workerLinkRepository = mock(WorkerLinkRepository.class);
        TaskTransitionRecorder transitionRecorder = mock(TaskTransitionRecorder.class);
        AuditEventRepository auditRepository = mock(AuditEventRepository.class);
        WorkerLinkGenerator workerLinkGenerator = mock(WorkerLinkGenerator.class);
        WorkerLinkHasher workerLinkHasher = mock(WorkerLinkHasher.class);
        TenantDatabaseContext tenantDatabaseContext = mock(TenantDatabaseContext.class);
        UuidGenerator uuidGenerator = mock(UuidGenerator.class);
        Task task = mock(Task.class);
        ApprovalRequest approval = mock(ApprovalRequest.class);

        when(task.workerId()).thenReturn(WORKER_ID);
        when(task.taskId()).thenReturn(TASK_ID);
        when(task.companyId()).thenReturn(COMPANY_ID);
        when(task.status()).thenReturn(TaskStatus.APPROVED);
        when(task.version()).thenReturn(0L);
        when(task.waitForWorker(0L, ACTOR_ID, NOW)).thenReturn(TaskStatus.APPROVED);
        when(task.contentRevision()).thenReturn(2L);
        when(task.criticalFingerprint()).thenReturn("approved-fingerprint");
        when(approval.isValidFor(2L, "approved-fingerprint")).thenReturn(true);
        when(taskRepository.findByIdAndCompanyId(TASK_ID, COMPANY_ID))
                .thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(approvalRepository.findLatestApprovedByTaskIdAndCompanyId(TASK_ID, COMPANY_ID))
                .thenReturn(Optional.of(approval));
        when(workerLinkHasher.hash("worker-link-issue-1")).thenReturn("idempotency-hash");
        when(workerLinkRepository.findByTaskIdAndIdempotencyKey(TASK_ID, "idempotency-hash"))
                .thenReturn(Optional.empty());
        when(workerLinkRepository.findActiveByTaskIdAndCompanyId(TASK_ID, COMPANY_ID))
                .thenReturn(Optional.empty());
        when(workerLinkGenerator.generate()).thenReturn(
                new WorkerLinkGenerator.GeneratedWorkerLinkToken("raw-token", "token-hash")
        );
        when(uuidGenerator.generate()).thenReturn(WORKER_LINK_ID);

        WorkerLinkService service = new WorkerLinkService(
                taskRepository,
                approvalRepository,
                companySettingsRepository,
                workerLinkRepository,
                transitionRecorder,
                auditRepository,
                workerLinkGenerator,
                workerLinkHasher,
                tenantDatabaseContext,
                uuidGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new ServiceFixture(service, companySettingsRepository);
    }

    private CompanySettings companySettings(long linkExpiryHours) {
        CompanySettings defaults = CompanySettings.defaults(COMPANY_ID, NOW);
        return defaults.update(
                defaults.approvalPolicy(),
                linkExpiryHours,
                defaults.evidenceRules(),
                defaults.fileRetentionDays(),
                defaults.aiLogRetentionDays(),
                defaults.auditVisibility(),
                NOW
        );
    }

    private WorkerLinkIssueCommand command(Long expiresInHours) {
        return new WorkerLinkIssueCommand(
                TASK_ID,
                expiresInHours,
                false,
                "worker-link-issue-1"
        );
    }

    private ActorContext actor() {
        return new ActorContext(ACTOR_ID, COMPANY_ID, Set.of(UserRole.HR));
    }

    private record ServiceFixture(
            WorkerLinkService service,
            CompanySettingsRepository companySettingsRepository
    ) {
    }
}
