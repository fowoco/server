package com.fowoco.server.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.notification.application.port.NotificationRepository;
import com.fowoco.server.notification.domain.Notification;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventActorType;
import com.fowoco.server.reliability.domain.SafeEventPayload;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationEventHandlerTest {

    private static final UUID COMPANY_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("94000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID NEW_ID = UUID.fromString("99000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final TenantDatabaseContext tenantDatabaseContext = mock(TenantDatabaseContext.class);
    private final UuidGenerator uuidGenerator = mock(UuidGenerator.class);
    private final Clock clock = Clock.fixed(NOW, java.time.ZoneOffset.UTC);

    private final NotificationEventHandler handler = new NotificationEventHandler(
            taskRepository, notificationRepository, tenantDatabaseContext, uuidGenerator, clock
    );

    @Test
    void supportsTaskCreatedAndApprovalRequested() {
        assertThat(handler.supports("TaskCreated")).isTrue();
        assertThat(handler.supports("ApprovalRequested")).isTrue();
        assertThat(handler.supports("WorkerResponseSubmitted")).isTrue();
        assertThat(handler.supports("TaskNeedsInfo")).isTrue();
        assertThat(handler.supports("TaskCancelled")).isFalse();
        assertThat(handler.supports("SomethingElse")).isFalse();
    }

    @Test
    void createsNotificationForAiCandidateTask() {
        Task task = aiCandidateTask();
        when(taskRepository.findByIdAndCompanyId(TASK_ID, COMPANY_ID)).thenReturn(Optional.of(task));
        when(uuidGenerator.generate()).thenReturn(NEW_ID);

        handler.handle(taskCreatedEvent());

        verify(notificationRepository).insert(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    void doesNotCreateNotificationForManualTask() {
        Task task = manualTask();
        when(taskRepository.findByIdAndCompanyId(TASK_ID, COMPANY_ID)).thenReturn(Optional.of(task));

        handler.handle(taskCreatedEvent());

        verify(notificationRepository, never()).insert(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    void createsNotificationForApprovalRequestedAndSendsToRequester() {
        when(uuidGenerator.generate()).thenReturn(NEW_ID);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        handler.handle(approvalRequestedEvent());

        verify(notificationRepository).insert(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.userId()).isEqualTo(ACTOR_ID);
        assertThat(notification.title()).contains("재계약 준비");
    }

    @Test
    void createsNotificationForWorkerResponseSubmitted() {
        when(uuidGenerator.generate()).thenReturn(NEW_ID);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        handler.handle(workerResponseSubmittedEvent());

        verify(notificationRepository).insert(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.userId()).isEqualTo(ACTOR_ID);
        assertThat(notification.title()).contains("문서 제출이 완료됐습니다");
        assertThat(notification.title()).contains("재계약 준비");
    }

    @Test
    void createsNotificationForTaskNeedsInfo() {
        when(uuidGenerator.generate()).thenReturn(NEW_ID);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        handler.handle(taskNeedsInfoEvent());

        verify(notificationRepository).insert(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.userId()).isEqualTo(ACTOR_ID);
        assertThat(notification.title()).contains("문서 보완이 필요합니다");
        assertThat(notification.title()).contains("재계약 준비");
    }

    private DomainEventEnvelope taskCreatedEvent() {
        return new DomainEventEnvelope(
                UUID.randomUUID(),
                "TaskCreated",
                "1",
                "Task",
                TASK_ID,
                COMPANY_ID,
                EventActorType.HR_USER,
                UUID.randomUUID(),
                "req-1",
                "12345678901234567890123456789012",
                NOW,
                SafeEventPayload.of(Set.of("source"), Map.of("source", TaskSource.AI_CANDIDATE))
        );
    }

    private DomainEventEnvelope approvalRequestedEvent() {
        return new DomainEventEnvelope(
                UUID.randomUUID(),
                "ApprovalRequested",
                "1",
                "Task",
                TASK_ID,
                COMPANY_ID,
                EventActorType.HR_USER,
                ACTOR_ID,
                "req-2",
                "12345678901234567890123456789012",
                NOW,
                SafeEventPayload.of(
                        Set.of("task_title", "task_type"),
                        Map.of("task_title", "재계약 준비", "task_type", TaskType.RECONTRACT)
                )
        );
    }

    private DomainEventEnvelope workerResponseSubmittedEvent() {
        return new DomainEventEnvelope(
                UUID.randomUUID(),
                "WorkerResponseSubmitted",
                "1",
                "Task",
                TASK_ID,
                COMPANY_ID,
                EventActorType.WORKER_LINK,
                ACTOR_ID,
                "worker-response-submit",
                "12345678901234567890123456789012",
                NOW,
                SafeEventPayload.of(
                        Set.of("task_title", "task_type"),
                        Map.of("task_title", "재계약 준비", "task_type", TaskType.RECONTRACT)
                )
        );
    }

    private DomainEventEnvelope taskNeedsInfoEvent() {
        return new DomainEventEnvelope(
                UUID.randomUUID(),
                "TaskNeedsInfo",
                "1",
                "Task",
                TASK_ID,
                COMPANY_ID,
                EventActorType.HR_USER,
                ACTOR_ID,
                "req-3",
                "12345678901234567890123456789012",
                NOW,
                SafeEventPayload.of(
                        Set.of("task_title", "task_type"),
                        Map.of("task_title", "재계약 준비", "task_type", TaskType.RECONTRACT)
                )
        );
    }

    private Task aiCandidateTask() {
        return Task.create(
                TASK_ID, COMPANY_ID, UUID.randomUUID(), UUID.randomUUID(),
                TaskType.RECONTRACT, "WF-CON-001", "0.2.0",
                "AI 추천: 재계약 준비", "설명", "{}", "a".repeat(64),
                TaskSource.AI_CANDIDATE, TaskStatus.DRAFT, null,
                UUID.randomUUID(), NOW
        );
    }

    private Task manualTask() {
        return Task.create(
                TASK_ID, COMPANY_ID, UUID.randomUUID(), UUID.randomUUID(),
                TaskType.RECONTRACT, "WF-CON-001", "0.2.0",
                "수동 재계약", "설명", "{}", "a".repeat(64),
                TaskSource.MANUAL, TaskStatus.DRAFT, null,
                UUID.randomUUID(), NOW
        );
    }
}
