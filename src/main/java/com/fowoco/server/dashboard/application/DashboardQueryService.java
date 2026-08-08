package com.fowoco.server.dashboard.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.dashboard.api.DashboardSummaryCountsResponse;
import com.fowoco.server.dashboard.api.DashboardTaskSummaryResponse;
import com.fowoco.server.dashboard.api.DashboardTodayResponse;
import com.fowoco.server.dashboard.api.UpcomingExpiryCategory;
import com.fowoco.server.dashboard.api.UpcomingExpiryItemResponse;
import com.fowoco.server.dashboard.application.error.DashboardErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.application.WorkerSearchQuery;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardQueryService {

    private static final int PRIORITY_TASK_LIMIT = 5;
    private static final int UPCOMING_DAYS = 7;

    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final Clock clock;

    public DashboardQueryService(
            TaskRepository taskRepository,
            WorkerRepository workerRepository,
            WorkerDocumentRepository workerDocumentRepository,
            TenantDatabaseContext tenantDatabaseContext,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
        this.workerDocumentRepository = workerDocumentRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardTodayResponse today(ActorContext actor, LocalDate date, String timezone) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        UUID companyId = actor.companyId();
        Clock effectiveClock = timezone != null ? clock.withZone(parseTimezone(timezone)) : clock;
        LocalDate targetDate = date != null ? date : LocalDate.now(effectiveClock);
        LocalDate windowEnd = targetDate.plusDays(UPCOMING_DAYS + 1);

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

        List<UpcomingExpiryItemResponse> upcoming7Days = collectUpcomingExpiry(companyId, targetDate, windowEnd);

        return new DashboardTodayResponse(summaryCounts, priorityTasks, upcoming7Days, pendingApproval, workerResponse);
    }

    private List<UpcomingExpiryItemResponse> collectUpcomingExpiry(UUID companyId, LocalDate windowStart, LocalDate windowEnd) {
        List<UpcomingExpiryItemResponse> result = new ArrayList<>();

        List<Worker> stayExpiringWorkers = workerRepository.findPage(
                companyId,
                new WorkerSearchQuery(null, null, windowEnd, null, null, null, 0, 100)
        );
        List<Worker> contractEndingWorkers = workerRepository.findPage(
                companyId,
                new WorkerSearchQuery(null, null, null, windowEnd, null, null, 0, 100)
        );
        List<Worker> permitEndingWorkers = workerRepository.findPage(
                companyId,
                new WorkerSearchQuery(null, null, null, null, windowEnd, null, 0, 100)
        );
        List<Worker> activityEndingWorkers = workerRepository.findPage(
                companyId,
                new WorkerSearchQuery(null, null, null, null, null, windowEnd, 0, 100)
        );

        addWorkerExpiry(result, stayExpiringWorkers, UpcomingExpiryCategory.STAY_EXPIRY, Worker::stayExpiryDate);
        addWorkerExpiry(result, contractEndingWorkers, UpcomingExpiryCategory.CONTRACT_END, Worker::contractEndDate);
        addWorkerExpiry(result, permitEndingWorkers, UpcomingExpiryCategory.EMPLOYMENT_PERMIT_END, Worker::employmentPermitEndDate);
        addWorkerExpiry(result, activityEndingWorkers, UpcomingExpiryCategory.EMPLOYMENT_ACTIVITY_END, Worker::employmentActivityEndDate);

        List<WorkerDocument> expiringDocuments = workerDocumentRepository.findPage(
                companyId,
                new WorkerDocumentSearchQuery(null, null, null, null, windowEnd, 0, 100)
        );
        Map<UUID, String> workerNames = expiringDocuments.isEmpty()
                ? Map.of()
                : workerRepository.findAllByWorkerIdsAndCompanyId(
                        expiringDocuments.stream().map(WorkerDocument::workerId).collect(Collectors.toSet()),
                        companyId
                ).stream().collect(Collectors.toMap(Worker::workerId, Worker::displayName));

        for (WorkerDocument document : expiringDocuments) {
            result.add(new UpcomingExpiryItemResponse(
                    document.workerId(),
                    workerNames.get(document.workerId()),
                    UpcomingExpiryCategory.DOCUMENT_EXPIRY,
                    document.expiryDate(),
                    document.documentType()
            ));
        }

        return result.stream()
                .filter(item -> !item.getExpiryDate().isBefore(windowStart))
                .toList();
    }

    private void addWorkerExpiry(
            List<UpcomingExpiryItemResponse> result,
            List<Worker> workers,
            UpcomingExpiryCategory category,
            java.util.function.Function<Worker, LocalDate> dateExtractor
    ) {
        for (Worker worker : workers) {
            result.add(new UpcomingExpiryItemResponse(
                    worker.workerId(),
                    worker.displayName(),
                    category,
                    dateExtractor.apply(worker),
                    null
            ));
        }
    }

    private ZoneId parseTimezone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ApiException(DashboardErrorCode.INVALID_TIMEZONE);
        }
    }
}
