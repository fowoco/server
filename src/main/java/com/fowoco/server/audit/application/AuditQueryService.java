package com.fowoco.server.audit.application;

import com.fowoco.server.audit.application.AuditCursorCodec.DecodedAuditCursor;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.application.port.AuditEventRepository.WorkerActivityRecord;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.settings.application.port.CompanySettingsRepository;
import com.fowoco.server.settings.domain.AuditVisibility;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.worker.application.error.WorkerErrorCode;
import com.fowoco.server.worker.application.port.WorkerRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final EnumSet<AuditAction> WORKER_ACTIVITY_ACTIONS = EnumSet.of(
            AuditAction.WORKER_LINK_SENT,
            AuditAction.WORKER_LINK_ACCESSED,
            AuditAction.WORKER_LINK_RESPONSE_SUBMITTED,
            AuditAction.WORKER_LINK_RESPONSES_REVIEWED
    );

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final AuditEventRepository auditRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final AuditCursorCodec cursorCodec;

    public AuditQueryService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            TaskRepository taskRepository,
            WorkerRepository workerRepository,
            AuditEventRepository auditRepository,
            CompanySettingsRepository companySettingsRepository,
            AuditCursorCodec cursorCodec
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
        this.auditRepository = auditRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.cursorCodec = cursorCodec;
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> getTaskActivities(UUID taskId, ActorContext actor) {
        bindTenant(actor);
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
        taskRepository.findByIdAndCompanyId(taskId, actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
        return auditRepository.findTaskActivities(actor.companyId(), taskId).stream()
                .map(AuditEventView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkerActivityPageResult getWorkerActivities(
            UUID workerId,
            String cursor,
            int requestedLimit,
            ActorContext actor
    ) {
        bindTenant(actor);
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
        workerRepository.findByWorkerIdAndCompanyId(workerId, actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_NOT_FOUND));
        int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        DecodedAuditCursor decodedCursor = cursorCodec.decode(cursor);
        List<WorkerActivityRecord> fetched = auditRepository.findWorkerActivities(
                new WorkerActivitySearchCriteria(
                        actor.companyId(),
                        workerId,
                        WORKER_ACTIVITY_ACTIONS,
                        decodedCursor.createdAt(),
                        decodedCursor.auditEventId(),
                        limit + 1
                )
        );
        boolean hasNext = fetched.size() > limit;
        List<WorkerActivityRecord> currentPage = hasNext
                ? fetched.subList(0, limit)
                : fetched;
        String nextCursor = hasNext
                ? cursorCodec.encode(
                        currentPage.get(currentPage.size() - 1).occurredAt(),
                        currentPage.get(currentPage.size() - 1).auditEventId()
                )
                : null;
        return new WorkerActivityPageResult(
                currentPage.stream().map(this::toWorkerActivity).toList(),
                nextCursor
        );
    }

    @Transactional(readOnly = true)
    public AuditPageResult search(
            ActorType actorType,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String traceId,
            Instant createdFrom,
            Instant createdTo,
            String cursor,
            int requestedLimit,
            ActorContext actor
    ) {
        bindTenant(actor);
        requireAuditSearchPermission(actor);
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        DecodedAuditCursor decodedCursor = cursorCodec.decode(cursor);
        List<AuditEvent> fetched = auditRepository.search(new AuditSearchCriteria(
                actor.companyId(),
                actorType,
                action,
                targetType,
                targetId,
                normalizeTraceId(traceId),
                createdFrom,
                createdTo,
                decodedCursor.createdAt(),
                decodedCursor.auditEventId(),
                limit + 1
        ));
        boolean hasNext = fetched.size() > limit;
        List<AuditEvent> currentPage = hasNext ? fetched.subList(0, limit) : fetched;
        String nextCursor = hasNext ? cursorCodec.encode(currentPage.get(currentPage.size() - 1)) : null;
        return new AuditPageResult(
                currentPage.stream().map(AuditEventView::from).toList(),
                nextCursor
        );
    }

    private String normalizeTraceId(String traceId) {
        return traceId == null || traceId.isBlank() ? null : traceId.trim();
    }

    private WorkerActivityView toWorkerActivity(WorkerActivityRecord record) {
        return switch (record.action()) {
            case WORKER_LINK_SENT -> workerActivity(
                    record,
                    WorkerActivityType.GUIDANCE_SENT,
                    "모바일 안내를 전송했습니다."
            );
            case WORKER_LINK_ACCESSED -> workerActivity(
                    record,
                    WorkerActivityType.GUIDANCE_OPENED,
                    "근로자가 모바일 안내를 열었습니다."
            );
            case WORKER_LINK_RESPONSE_SUBMITTED -> workerActivity(
                    record,
                    WorkerActivityType.WORKER_RESPONSE_SUBMITTED,
                    "근로자가 안내에 응답했습니다."
            );
            case WORKER_LINK_RESPONSES_REVIEWED -> workerActivity(
                    record,
                    WorkerActivityType.RESPONSE_REVIEWED,
                    "담당자가 근로자 응답을 확인했습니다."
            );
            default -> throw new IllegalStateException(
                    "Unsupported worker activity action: " + record.action()
            );
        };
    }

    private WorkerActivityView workerActivity(
            WorkerActivityRecord record,
            WorkerActivityType type,
            String summary
    ) {
        return new WorkerActivityView(
                record.auditEventId(),
                type,
                record.taskId(),
                record.taskTitle(),
                summary,
                record.occurredAt()
        );
    }

    private void requireAuditSearchPermission(ActorContext actor) {
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR);
        AuditVisibility visibility = companySettingsRepository
                .findByCompanyId(actor.companyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Persisted company settings are missing for company "
                                + actor.companyId()
                ))
                .auditVisibility();
        if (actor.roles().stream().noneMatch(visibility::permits)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void bindTenant(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
    }
}
