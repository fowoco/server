package com.fowoco.server.casework.application;

import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.casework.application.error.CaseErrorCode;
import com.fowoco.server.casework.application.port.CaseQueryRepository;
import com.fowoco.server.casework.application.port.CaseQueryRepository.CaseRecord;
import com.fowoco.server.casework.application.port.CaseQueryRepository.CaseRecordPage;
import com.fowoco.server.casework.application.port.CaseQueryRepository.CaseTaskRecord;
import com.fowoco.server.casework.domain.CaseLifecycleStatus;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CaseQueryService {

    private static final Comparator<CaseTaskProjection> CURRENT_TASK_ORDER = Comparator
            .comparing(CaseTaskProjection::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CaseTaskProjection::taskId);

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final CaseQueryRepository repository;
    private final CaseDisplayStatusResolver displayStatusResolver;
    private final ObjectMapper objectMapper;

    public CaseQueryService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            CaseQueryRepository repository,
            ObjectMapper objectMapper
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.repository = repository;
        this.displayStatusResolver = new CaseDisplayStatusResolver();
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CasePageResult findPage(CaseSearchQuery query, ActorContext actor) {
        bindAndAuthorize(actor);
        CaseRecordPage page = repository.findPage(actor.companyId(), query);
        Map<UUID, List<CaseTaskProjection>> tasksByCase = tasksByCase(
                actor.companyId(),
                page.items().stream().map(CaseRecord::caseId).toList()
        );
        List<CaseProjection> items = page.items().stream()
                .map(item -> toProjection(item, tasksByCase.getOrDefault(item.caseId(), List.of())))
                .toList();
        return new CasePageResult(items, query.page(), query.size(), page.totalElements());
    }

    @Transactional(readOnly = true)
    public CaseProjection findById(UUID caseId, ActorContext actor) {
        bindAndAuthorize(actor);
        CaseRecord record = repository.findById(actor.companyId(), caseId)
                .orElseThrow(() -> new ApiException(CaseErrorCode.CASE_NOT_FOUND));
        List<CaseTaskProjection> tasks = repository.findTasks(actor.companyId(), List.of(caseId))
                .stream()
                .map(CaseQueryService::toTaskProjection)
                .toList();
        return toProjection(record, tasks);
    }

    private void bindAndAuthorize(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
    }

    private Map<UUID, List<CaseTaskProjection>> tasksByCase(UUID companyId, List<UUID> caseIds) {
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        return repository.findTasks(companyId, caseIds).stream()
                .collect(Collectors.groupingBy(
                        CaseTaskRecord::caseId,
                        Collectors.mapping(CaseQueryService::toTaskProjection, Collectors.toList())
                ));
    }

    private CaseProjection toProjection(CaseRecord record, List<CaseTaskProjection> tasks) {
        Map<String, Object> workflowSnapshot = decodeSnapshot(record.workflowSnapshotJson());
        Map<UUID, SnapshotStep> snapshotSteps = snapshotSteps(workflowSnapshot);
        List<CaseTaskProjection> orderedTasks = tasks.stream()
                .sorted(Comparator
                        .comparingInt((CaseTaskProjection task) -> snapshotSteps
                                .getOrDefault(task.taskId(), SnapshotStep.fallback())
                                .order())
                        .thenComparing(CURRENT_TASK_ORDER))
                .toList();
        List<CaseTaskProjection> countedTasks = orderedTasks.stream()
                .filter(task -> task.status() != TaskStatus.CANCELLED)
                .toList();
        int completedSteps = (int) countedTasks.stream()
                .filter(task -> task.status() == TaskStatus.COMPLETED)
                .count();
        int totalSteps = countedTasks.size();
        boolean completed = record.lifecycleStatus() == CaseLifecycleStatus.COMPLETED
                || (totalSteps > 0 && completedSteps == totalSteps);
        CaseDisplayState displayState = displayStatusResolver.resolve(new CaseDisplayFacts(
                completed,
                record.lifecycleStatus() == CaseLifecycleStatus.CANCELLED,
                record.linkSent(),
                record.reviewRequired(),
                record.unreadResponse()
        ));
        Map<UUID, TaskStatus> statusesByTaskId = orderedTasks.stream()
                .collect(Collectors.toMap(CaseTaskProjection::taskId, CaseTaskProjection::status));
        CaseTaskProjection currentTask = orderedTasks.stream()
                .filter(task -> !task.status().isTerminal())
                .filter(task -> dependencySatisfied(task, snapshotSteps, statusesByTaskId))
                .findFirst()
                .orElse(null);
        LocalDate dueDate = orderedTasks.stream()
                .filter(task -> !task.status().isTerminal())
                .map(CaseTaskProjection::dueDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        int percentage = totalSteps == 0 ? 0 : (completedSteps * 100) / totalSteps;
        return new CaseProjection(
                record.caseId(),
                record.workerId(),
                record.workerDisplayName(),
                record.title(),
                record.lifecycleStatus(),
                displayState.status(),
                displayState.hasUnreadResponse(),
                record.priority(),
                new CaseProgress(completedSteps, totalSteps, percentage),
                new CaseReadiness(
                        record.completedChecklistItems(),
                        record.totalChecklistItems(),
                        record.verifiedDocuments(),
                        record.totalDocuments(),
                        record.pendingApprovals(),
                        record.approvedApprovals(),
                        record.workerResponses(),
                        record.evidenceItems()
                ),
                dueDate,
                currentTask,
                orderedTasks,
                record.workflowCatalogVersion(),
                workflowSnapshot,
                record.updatedAt()
        );
    }

    private boolean dependencySatisfied(
            CaseTaskProjection task,
            Map<UUID, SnapshotStep> snapshotSteps,
            Map<UUID, TaskStatus> statusesByTaskId
    ) {
        SnapshotStep step = snapshotSteps.get(task.taskId());
        if (step == null || step.dependsOnTaskId() == null) {
            return true;
        }
        return statusesByTaskId.get(step.dependsOnTaskId()) == TaskStatus.COMPLETED;
    }

    private Map<UUID, SnapshotStep> snapshotSteps(Map<String, Object> snapshot) {
        Object rawSteps = snapshot.get("steps");
        if (!(rawSteps instanceof List<?> steps)) {
            return Map.of();
        }
        Map<UUID, SnapshotStep> result = new HashMap<>();
        for (Object rawStep : steps) {
            if (!(rawStep instanceof Map<?, ?> step)) {
                continue;
            }
            UUID taskId = uuidValue(step.get("task_id"));
            if (taskId == null) {
                continue;
            }
            int order = step.get("order") instanceof Number number
                    ? number.intValue()
                    : Integer.MAX_VALUE;
            UUID dependsOnTaskId = null;
            if (step.get("required_conditions") instanceof Map<?, ?> conditions) {
                dependsOnTaskId = uuidValue(conditions.get("depends_on_task_id"));
            }
            result.put(taskId, new SnapshotStep(order, dependsOnTaskId));
        }
        return Map.copyOf(result);
    }

    private UUID uuidValue(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static CaseTaskProjection toTaskProjection(CaseTaskRecord task) {
        return new CaseTaskProjection(
                task.taskId(),
                task.taskType(),
                task.title(),
                task.status(),
                task.dueDate()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeSnapshot(String snapshotJson) {
        try {
            Map<String, Object> snapshot = objectMapper.readValue(snapshotJson, Map.class);
            return Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
        } catch (JacksonException | NullPointerException exception) {
            throw new IllegalStateException("저장된 Workflow Snapshot을 읽을 수 없습니다.", exception);
        }
    }

    private record SnapshotStep(int order, UUID dependsOnTaskId) {

        private static SnapshotStep fallback() {
            return new SnapshotStep(Integer.MAX_VALUE, null);
        }
    }
}
