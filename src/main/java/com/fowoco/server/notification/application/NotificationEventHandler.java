package com.fowoco.server.notification.application;

import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.notification.application.port.NotificationRepository;
import com.fowoco.server.notification.domain.Notification;
import com.fowoco.server.notification.domain.NotificationTargetType;
import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskSource;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class NotificationEventHandler implements DomainEventHandler {

    private static final String HANDLER_NAME = "notificationFromTaskEvents";
    private static final String TASK_CREATED = "TaskCreated";
    private static final String APPROVAL_REQUESTED = "ApprovalRequested";
    private static final String WORKER_RESPONSE_SUBMITTED = "WorkerResponseSubmitted";
    private static final String TASK_NEEDS_INFO = "TaskNeedsInfo";
    private static final Set<String> SUPPORTED_EVENTS =
            Set.of(TASK_CREATED, APPROVAL_REQUESTED, WORKER_RESPONSE_SUBMITTED, TASK_NEEDS_INFO);

    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public NotificationEventHandler(
            TaskRepository taskRepository,
            NotificationRepository notificationRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Override
    public String handlerName() {
        return HANDLER_NAME;
    }

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED_EVENTS.contains(eventType);
    }

    @Override
    public void handle(DomainEventEnvelope event) {
        if (TASK_CREATED.equals(event.eventType())) {
            handleTaskCreated(event);
        } else if (APPROVAL_REQUESTED.equals(event.eventType())) {
            handleApprovalRequested(event);
        } else if (WORKER_RESPONSE_SUBMITTED.equals(event.eventType())) {
            handleWorkerResponseSubmitted(event);
        } else if (TASK_NEEDS_INFO.equals(event.eventType())) {
            handleTaskNeedsInfo(event);
        }
    }

    private void handleTaskCreated(DomainEventEnvelope event) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(event.companyId());
        Task task = taskRepository.findByIdAndCompanyId(event.aggregateId(), event.companyId())
                .orElseThrow(() -> new IllegalStateException("task not found for TaskCreated event"));

        if (task.source() != TaskSource.AI_CANDIDATE) {
            return;
        }

        Notification notification = Notification.create(
                uuidGenerator.generate(),
                task.companyId(),
                event.actorId(),
                NotificationTargetType.TASK,
                task.taskId(),
                "Agent 분석이 완료됐습니다: " + task.title(),
                event.occurredAt(),
                clock.instant()
        );
        notificationRepository.insert(notification);
    }

    private void handleApprovalRequested(DomainEventEnvelope event) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(event.companyId());
        Object taskTitle = event.payload().values().get("task_title");

        Notification notification = Notification.create(
                uuidGenerator.generate(),
                event.companyId(),
                event.actorId(),
                NotificationTargetType.TASK,
                event.aggregateId(),
                "승인 요청이 도착했습니다: " + taskTitle,
                event.occurredAt(),
                clock.instant()
        );
        notificationRepository.insert(notification);
    }

    private void handleWorkerResponseSubmitted(DomainEventEnvelope event) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(event.companyId());
        Object taskTitle = event.payload().values().get("task_title");

        Notification notification = Notification.create(
                uuidGenerator.generate(),
                event.companyId(),
                event.actorId(),
                NotificationTargetType.TASK,
                event.aggregateId(),
                "문서 제출이 완료됐습니다: " + taskTitle,
                event.occurredAt(),
                clock.instant()
        );
        notificationRepository.insert(notification);
    }

    private void handleTaskNeedsInfo(DomainEventEnvelope event) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(event.companyId());
        Object taskTitle = event.payload().values().get("task_title");

        Notification notification = Notification.create(
                uuidGenerator.generate(),
                event.companyId(),
                event.actorId(),
                NotificationTargetType.TASK,
                event.aggregateId(),
                "문서 보완이 필요합니다: " + taskTitle,
                event.occurredAt(),
                clock.instant()
        );
        notificationRepository.insert(notification);
    }
}
