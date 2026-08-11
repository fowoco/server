package com.fowoco.server.notification.application;

import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.notification.application.port.NotificationRepository;
import com.fowoco.server.notification.domain.Notification;
import com.fowoco.server.notification.domain.NotificationTargetType;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DueSoonNotificationScheduler {

    private static final int UPCOMING_DAYS = 7;

    private final CompanyRepository companyRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public DueSoonNotificationScheduler(
            CompanyRepository companyRepository,
            TaskRepository taskRepository,
            NotificationRepository notificationRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.companyRepository = companyRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void notifyDueSoonTasks() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        LocalDate windowEnd = today.plusDays(UPCOMING_DAYS);

        companyRepository.findAllIds().forEach(companyId ->
                processCompany(companyId, today, windowEnd)
        );
    }

    private void processCompany(UUID companyId, LocalDate today, LocalDate windowEnd) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
        taskRepository.findOpenTasksDueBetween(companyId, today, windowEnd)
                .forEach(this::createDueSoonNotification);
    }

    private void createDueSoonNotification(Task task) {
        Notification notification = Notification.create(
                uuidGenerator.generate(),
                task.companyId(),
                task.createdBy(),
                NotificationTargetType.TASK,
                task.taskId(),
                "마감이 임박했습니다: " + task.title(),
                clock.instant(),
                clock.instant()
        );
        notificationRepository.insert(notification);
    }
}
