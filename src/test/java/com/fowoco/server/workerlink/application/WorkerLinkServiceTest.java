package com.fowoco.server.workerlink.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.workerlink.application.port.WorkerLinkGenerator;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import java.time.Clock;
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

    @Test
    void bindsActorTenantBeforeFirstRepositoryAccess() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        ApprovalRequestRepository approvalRepository = mock(ApprovalRequestRepository.class);
        WorkerLinkRepository workerLinkRepository = mock(WorkerLinkRepository.class);
        TenantDatabaseContext tenantDatabaseContext = mock(TenantDatabaseContext.class);
        when(taskRepository.findByIdAndCompanyId(TASK_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        WorkerLinkService service = new WorkerLinkService(
                taskRepository,
                approvalRepository,
                workerLinkRepository,
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

        assertThatThrownBy(() -> service.issue(command, actor))
                .isInstanceOf(ApiException.class);

        InOrder order = inOrder(tenantDatabaseContext, taskRepository);
        order.verify(tenantDatabaseContext).setCompanyIdForCurrentTransaction(COMPANY_ID);
        order.verify(taskRepository).findByIdAndCompanyId(TASK_ID, COMPANY_ID);
    }
}
