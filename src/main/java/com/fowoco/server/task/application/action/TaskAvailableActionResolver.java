package com.fowoco.server.task.application.action;

import com.fowoco.server.aiintegration.application.renewal.RenewalWorkflowPolicy;
import com.fowoco.server.task.application.TaskResult;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskChecklistItem;
import com.fowoco.server.task.domain.TaskTargetType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TaskAvailableActionResolver {

    private static final String DOCUMENT_OCR = "DOCUMENT_OCR";
    private static final String USER_INPUT = "USER_INPUT";

    public TaskActionDecision resolve(TaskResult result) {
        Task task = result.task();
        if (task.status().isTerminal()) {
            return TaskActionDecision.terminal();
        }

        return switch (task.status()) {
            case DRAFT, NEEDS_INFO -> resolvePreparation(result);
            case READY_FOR_REVIEW -> TaskActionDecision.of(
                    TaskAvailableAction.APPROVE,
                    "HR_REVIEW_REQUIRED",
                    TaskAvailableAction.APPROVE
            );
            case APPROVED -> TaskActionDecision.of(
                    TaskAvailableAction.ISSUE_WORKER_LINK,
                    null,
                    TaskAvailableAction.ISSUE_WORKER_LINK
            );
            case WAITING_WORKER -> TaskActionDecision.of(
                    TaskAvailableAction.REVIEW_WORKER_RESPONSE,
                    "WORKER_RESPONSE_PENDING",
                    TaskAvailableAction.REVIEW_WORKER_RESPONSE
            );
            case WAITING_EXTERNAL -> TaskActionDecision.of(
                    TaskAvailableAction.COMPLETE_TASK,
                    "EXTERNAL_EVIDENCE_REQUIRED",
                    TaskAvailableAction.COMPLETE_TASK
            );
            case COMPLETED, CANCELLED -> TaskActionDecision.terminal();
        };
    }

    private TaskActionDecision resolvePreparation(TaskResult result) {
        Task task = result.task();
        RenewalProgress renewal = RenewalProgress.from(result.businessData());
        boolean renewalSupported = supportsRenewal(task);

        if (renewalSupported && !renewal.executed()) {
            return TaskActionDecision.of(
                    TaskAvailableAction.RUN_RENEWAL,
                    null,
                    TaskAvailableAction.RUN_RENEWAL
            );
        }
        if (renewalSupported && renewal.hasMissingSource(DOCUMENT_OCR)) {
            return TaskActionDecision.of(
                    TaskAvailableAction.REVIEW_OCR,
                    "OCR_REVIEW_REQUIRED",
                    TaskAvailableAction.REVIEW_OCR
            );
        }
        if (renewalSupported && renewal.hasMissingSource(USER_INPUT)) {
            return TaskActionDecision.of(
                    TaskAvailableAction.RUN_RENEWAL,
                    "RENEWAL_INPUT_REQUIRED",
                    TaskAvailableAction.RUN_RENEWAL
            );
        }
        if (!result.missingRequiredSlots().isEmpty()) {
            return TaskActionDecision.of(
                    TaskAvailableAction.PROVIDE_REQUIRED_INFORMATION,
                    "REQUIRED_INFORMATION_MISSING",
                    TaskAvailableAction.PROVIDE_REQUIRED_INFORMATION
            );
        }
        if (hasIncompleteRequiredChecklist(result.checklistItems())) {
            return TaskActionDecision.of(
                    TaskAvailableAction.COMPLETE_CHECKLIST,
                    "CHECKLIST_INCOMPLETE",
                    TaskAvailableAction.COMPLETE_CHECKLIST
            );
        }
        if (renewal.guideReviewRequired()) {
            return TaskActionDecision.of(
                    TaskAvailableAction.REVIEW_WORKER_GUIDE,
                    "WORKER_GUIDE_REVIEW_REQUIRED",
                    TaskAvailableAction.REVIEW_WORKER_GUIDE
            );
        }

        List<TaskAvailableAction> available = new ArrayList<>();
        if (renewal.generatedDocumentPresent()) {
            available.add(TaskAvailableAction.REVIEW_GENERATED_DOCUMENT);
        }
        available.add(TaskAvailableAction.REQUEST_APPROVAL);
        return new TaskActionDecision(
                TaskAvailableAction.REQUEST_APPROVAL,
                available,
                "APPROVAL_REQUIRED_BEFORE_CONTINUATION"
        );
    }

    private boolean supportsRenewal(Task task) {
        return task.targetType() == TaskTargetType.WORKER
                && RenewalWorkflowPolicy.supports(task.taskType().name(), task.workflowId());
    }

    private boolean hasIncompleteRequiredChecklist(List<TaskChecklistItem> items) {
        return items.stream().anyMatch(item -> item.required() && !item.completed());
    }

    private record RenewalProgress(
            boolean executed,
            Set<String> missingSlots,
            Map<String, String> sourceByField,
            boolean guideReviewRequired,
            boolean generatedDocumentPresent
    ) {
        private static RenewalProgress from(Map<String, Object> businessData) {
            Object executionValue = businessData.get("renewal_execution");
            if (!(executionValue instanceof Map<?, ?> execution)) {
                return new RenewalProgress(false, Set.of(), Map.of(), false, false);
            }

            Set<String> missingSlots = stringSet(execution.get("missing_slots"));
            Map<String, String> sources = requestedFieldSources(execution.get("requested_fields"));
            boolean guideReviewRequired = Boolean.TRUE.equals(execution.get("guide_review_required"));
            boolean generatedDocumentPresent = execution.get("generated_documents") instanceof List<?> documents
                    && !documents.isEmpty();
            return new RenewalProgress(
                    true,
                    missingSlots,
                    sources,
                    guideReviewRequired,
                    generatedDocumentPresent
            );
        }

        private boolean hasMissingSource(String source) {
            return missingSlots.stream().anyMatch(slot -> source.equals(sourceByField.get(slot)));
        }

        private static Set<String> stringSet(Object value) {
            if (!(value instanceof List<?> values)) {
                return Set.of();
            }
            Set<String> result = new LinkedHashSet<>();
            values.forEach(candidate -> {
                if (candidate instanceof String text && !text.isBlank()) {
                    result.add(text);
                }
            });
            return Set.copyOf(result);
        }

        private static Map<String, String> requestedFieldSources(Object value) {
            if (!(value instanceof List<?> fields)) {
                return Map.of();
            }
            Map<String, String> result = new LinkedHashMap<>();
            fields.forEach(candidate -> {
                if (!(candidate instanceof Map<?, ?> field)) {
                    return;
                }
                Object key = field.get("key");
                Object source = field.get("source_hint");
                if (key instanceof String keyText && source instanceof String sourceText) {
                    result.put(keyText, sourceText);
                }
            });
            return Map.copyOf(result);
        }
    }
}
