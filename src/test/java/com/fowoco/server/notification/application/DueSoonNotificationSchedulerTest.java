package com.fowoco.server.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.notification.application.port.NotificationRepository;
import com.fowoco.server.notification.domain.Notification;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DueSoonNotificationSchedulerTest {

    private static final UUID COMPANY_A = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID CREATOR_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID NEW_ID = UUID.fromString("99000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-10T03:00:00Z");

    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final TenantDatabaseContext tenantDatabaseContext = mock(TenantDatabaseContext.class);
    private final UuidGenerator uuidGenerator = mock(UuidGenerator.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final DueSoonNotificationScheduler scheduler = new DueSoonNotificationScheduler(
            companyRepository, taskRepository, notificationRepository, tenantDatabaseContext, uuidGenerator, clock
    );

    @Test
    void createsNotificationForEachDueSoonTaskPerCompany() {
        when(companyRepository.findAllIds()).thenReturn(List.of(COMPANY_A, COMPANY_B));
        Task taskA = dueSoonTask(COMPANY_A);
        Task taskB1 = dueSoonTask(COMPANY_B);
        Task taskB2 = dueSoonTask(COMPANY_B);
        when(taskRepository.findOpenTasksDueBetween(
                org.mockito.ArgumentMatchers.eq(COMPANY_A),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(taskA));
        when(taskRepository.findOpenTasksDueBetween(
                org.mockito.ArgumentMatchers.eq(COMPANY_B),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(taskB1, taskB2));
        when(uuidGenerator.generate()).thenReturn(NEW_ID);

        scheduler.notifyDueSoonTasks();

        verify(notificationRepository, times(3)).insert(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    void notificationGoesToTaskCreator() {
        when(companyRepository.findAllIds()).thenReturn(List.of(COMPANY_A));
        Task task = dueSoonTask(COMPANY_A);
        when(taskRepository.findOpenTasksDueBetween(
                org.mockito.ArgumentMatchers.eq(COMPANY_A),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(task));
        when(uuidGenerator.generate()).thenReturn(NEW_ID);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        scheduler.notifyDueSoonTasks();

        verify(notificationRepository).insert(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.userId()).isEqualTo(CREATOR_ID);
        assertThat(notification.title()).contains("마감이 임박했습니다");
    }

    @Test
    void queriesUsingKoreaDateNotUtcDate() {
        when(companyRepository.findAllIds()).thenReturn(List.of(COMPANY_A));
        when(taskRepository.findOpenTasksDueBetween(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of());
        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);

        scheduler.notifyDueSoonTasks();

        verify(taskRepository).findOpenTasksDueBetween(
                org.mockito.ArgumentMatchers.eq(COMPANY_A), fromCaptor.capture(), toCaptor.capture()
        );
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 17));
    }

    private Task dueSoonTask(UUID companyId) {
        return Task.create(
                UUID.randomUUID(), companyId, UUID.randomUUID(), UUID.randomUUID(),
                TaskType.RECONTRACT, "WF-CON-001", "0.2.0",
                "재계약 준비", "설명", "{}", "a".repeat(64),
                TaskSource.MANUAL, TaskStatus.DRAFT, LocalDate.of(2026, 8, 14),
                CREATOR_ID, NOW
        );
    }
}
