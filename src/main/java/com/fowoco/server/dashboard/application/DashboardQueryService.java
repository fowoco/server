package com.fowoco.server.dashboard.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.dashboard.api.DashboardSummaryCountsResponse;
import com.fowoco.server.dashboard.api.DashboardTaskSummaryResponse;
import com.fowoco.server.dashboard.api.DashboardTodayResponse;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardQueryService {

    private static final int PRIORITY_TASK_LIMIT = 5;

    private final TaskRepository taskRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final Clock clock;

    public DashboardQueryService(
            TaskRepository taskRepository,
            TenantDatabaseContext tenantDatabaseContext,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardTodayResponse today(ActorContext actor, LocalDate date, String timezone) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        UUID companyId = actor.companyId();
        Clock effectiveClock = timezone != null ? clock.withZone(java.time.ZoneId.of(timezone)) : clock;
        LocalDate targetDate = date != null ? date : LocalDate.now(effectiveClock);

        long pendingApproval = taskRepository.countByCompanyIdAndStatus(companyId, TaskStatus.READY_FOR_REVIEW);
        long needsInfo = taskRepository.countByCompanyIdAndStatus(companyId, TaskStatus.NEEDS_INFO);
        long workerResponse = taskRepository.countByCompanyIdAndStatus(companyId, TaskStatus.WAITING_WORKER);
        long dueToday = taskRepository.countOpenTasksDueOn(companyId, targetDate);

        DashboardSummaryCountsResponse summaryCounts = new DashboardSummaryCountsResponse(
                pendingApproval, dueToday, needsInfo, workerResponse
        );

        List<Task> openTasks = taskRepository.findOpenTasks(companyId, PRIORITY_TASK_LIMIT);
        List<DashboardTaskSummaryResponse> priorityTasks = openTasks.stream()
                .map(DashboardTaskSummaryResponse::from)
                .toList();

        return new DashboardTodayResponse(summaryCounts, priorityTasks, pendingApproval, workerResponse);
    }
}
