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
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class NotificationEventHandler implements DomainEventHandler {

    private static final String HANDLER_NAME = "notificationFromTaskCreated";
    private static final String TASK_CREATED = "TaskCreated";

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
        return TASK_CREATED.equals(eventType);
    }

    @Override
    public void handle(DomainEventEnvelope event) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(event.companyId());
        Task task = taskRepository.findByIdAndCompanyId(event.aggregateId(), event.companyId())
                .orElseThrow(() -> new IllegalStateException("task not found for TaskCreated event"));

        if (task.source() != TaskSource.AI_CANDIDATE) {
            return;
        }

        Notification notification = Notification.create(
                uuidGenerator.generate(),
                task.companyId(),
                NotificationTargetType.TASK,
                task.taskId(),
                "/tasks/" + task.taskId(),
                "Agent 분석이 완료됐습니다: " + task.title(),
                event.occurredAt(),
                clock.instant()
        );
        notificationRepository.insert(notification);
    }
}
