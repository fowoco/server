package com.fowoco.server.audit.application;

import com.fowoco.server.audit.application.AuditCursorCodec.DecodedAuditCursor;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ActorAuthorizer actorAuthorizer;
    private final TaskRepository taskRepository;
    private final AuditEventRepository auditRepository;
    private final AuditCursorCodec cursorCodec;

    public AuditQueryService(
            ActorAuthorizer actorAuthorizer,
            TaskRepository taskRepository,
            AuditEventRepository auditRepository,
            AuditCursorCodec cursorCodec
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.taskRepository = taskRepository;
        this.auditRepository = auditRepository;
        this.cursorCodec = cursorCodec;
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> getTaskActivities(UUID taskId, ActorContext actor) {
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
        taskRepository.findByIdAndCompanyId(taskId, actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
        return auditRepository.findTaskActivities(actor.companyId(), taskId).stream()
                .map(AuditEventView::from)
                .toList();
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
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN);
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
}
