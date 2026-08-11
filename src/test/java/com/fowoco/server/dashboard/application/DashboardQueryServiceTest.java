package com.fowoco.server.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardQueryServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    // UTC 기준 2026-08-07T18:00:00Z = 한국 시간(KST, UTC+9) 2026-08-08T03:00:00
    // 즉 한국은 이미 8월 8일이지만, UTC는 아직 8월 7일인 시점
    private static final Clock FIXED_UTC_CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void dueTodayUsesKoreaDateWhenTimezoneOmitted() {
        FakeTaskRepository taskRepository = new FakeTaskRepository();
        DashboardQueryService service = new DashboardQueryService(
                taskRepository,
                new NoopWorkerRepository(),
                new NoopWorkerDocumentRepository(),
                new NoopTenantDatabaseContext(),
                FIXED_UTC_CLOCK
        );

        service.today(actorContext(), null, null);

        // timezone을 생략했으니, "오늘"은 한국 날짜(2026-08-08)로 계산되어야 한다.
        // UTC 그대로였다면 2026-08-07로 잘못 계산되었을 것이다.
        assertThat(taskRepository.lastDueTodayDate).isEqualTo(LocalDate.of(2026, 8, 8));
    }

    private ActorContext actorContext() {
        return new ActorContext(UUID.randomUUID(), COMPANY_ID, Set.of(UserRole.HR));
    }

    private static final class FakeTaskRepository implements TaskRepository {
        private LocalDate lastDueTodayDate;

        @Override
        public java.util.Optional<Task> findByIdAndCompanyId(UUID taskId, UUID companyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskPage findAll(TaskSearchCriteria criteria) {
            return new TaskPage(List.of(), 0, 0, 0, 0);
        }

        @Override
        public Task save(Task task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Task> findOpenTasks(UUID companyId, int limit) {
            return List.of();
        }

        @Override
        public long countByCompanyIdAndStatus(UUID companyId, TaskStatus status) {
            return 0;
        }

        @Override
        public long countOpenTasksDueOn(UUID companyId, LocalDate dueDate) {
            this.lastDueTodayDate = dueDate;
            return 0;
        }

        @Override
        public long countOpenTasksByCompanyId(UUID companyId) {
            return 0;
        }

        @Override
        public List<Task> findOpenTasksDueBetween(UUID companyId, LocalDate from, LocalDate to) {
            return List.of();
        }
    }

    private static final class NoopWorkerRepository implements WorkerRepository {
        @Override
        public void insert(com.fowoco.server.worker.domain.Worker worker) {
        }

        @Override
        public java.util.Optional<com.fowoco.server.worker.domain.Worker> findByWorkerIdAndCompanyId(
                UUID workerId, UUID companyId) {
            return java.util.Optional.empty();
        }

        @Override
        public com.fowoco.server.worker.domain.Worker update(com.fowoco.server.worker.domain.Worker worker) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.fowoco.server.worker.domain.Worker> findPage(
                UUID companyId, com.fowoco.server.worker.application.WorkerSearchQuery query) {
            return List.of();
        }

        @Override
        public long countPage(UUID companyId, com.fowoco.server.worker.application.WorkerSearchQuery query) {
            return 0;
        }

        @Override
        public List<com.fowoco.server.worker.domain.Worker> findAllByWorkerIdsAndCompanyId(
                Set<UUID> workerIds, UUID companyId) {
            return List.of();
        }
    }

    private static final class NoopWorkerDocumentRepository implements WorkerDocumentRepository {
        @Override
        public void insert(com.fowoco.server.worker.domain.WorkerDocument document) {
        }

        @Override
        public java.util.Optional<com.fowoco.server.worker.domain.WorkerDocument> findByIdAndWorkerIdAndCompanyId(
                UUID workerDocumentId, UUID workerId, UUID companyId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<com.fowoco.server.worker.domain.WorkerDocument> findByIdAndCompanyId(
                UUID workerDocumentId, UUID companyId) {
            return java.util.Optional.empty();
        }

        @Override
        public com.fowoco.server.worker.domain.WorkerDocument update(
                com.fowoco.server.worker.domain.WorkerDocument document) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.fowoco.server.worker.domain.WorkerDocument> findPage(
                UUID companyId, com.fowoco.server.worker.application.WorkerDocumentSearchQuery query) {
            return List.of();
        }

        @Override
        public long countPage(UUID companyId, com.fowoco.server.worker.application.WorkerDocumentSearchQuery query) {
            return 0;
        }
    }

    private static final class NoopTenantDatabaseContext implements TenantDatabaseContext {
        @Override
        public void setCompanyIdForCurrentTransaction(UUID companyId) {
        }
    }
}
