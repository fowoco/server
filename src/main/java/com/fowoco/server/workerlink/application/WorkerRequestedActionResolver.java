package com.fowoco.server.workerlink.application;

import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.domain.Task;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkerRequestedActionResolver {

    private static final int MAX_TEXT_LENGTH = 500;
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999");
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "passport_number",
            "alien_registration_number",
            "date_of_birth",
            "full_name",
            "legal_name",
            "phone",
            "account_number"
    );
    private static final Map<String, FieldDefinition> WORKER_FIELDS = Map.ofEntries(
            Map.entry("lodging", new FieldDefinition(
                    "현재 숙소 제공 조건을 입력해 주세요.", WorkerRequestedActionInputType.TEXT
            )),
            Map.entry("accommodation_provided", new FieldDefinition(
                    "사업장에서 숙소를 제공받고 있나요?", WorkerRequestedActionInputType.BOOLEAN
            )),
            Map.entry("accommodation_business_building", new FieldDefinition(
                    "숙소가 사업장 건물 안에 있나요?", WorkerRequestedActionInputType.BOOLEAN
            )),
            Map.entry("accommodation_cost", new FieldDefinition(
                    "매월 부담하는 숙소 비용을 입력해 주세요.", WorkerRequestedActionInputType.MONEY
            )),
            Map.entry("meal_provided", new FieldDefinition(
                    "사업장에서 식사를 제공받고 있나요?", WorkerRequestedActionInputType.BOOLEAN
            )),
            Map.entry("meal_cost", new FieldDefinition(
                    "매월 부담하는 식사 비용을 입력해 주세요.", WorkerRequestedActionInputType.MONEY
            )),
            Map.entry("worker_renewal_intent", new FieldDefinition(
                    "계속 근무하고 재계약을 진행하시겠습니까?", WorkerRequestedActionInputType.BOOLEAN
            ))
    );

    private final TaskContentCodec taskContentCodec;

    public WorkerRequestedActionResolver(TaskContentCodec taskContentCodec) {
        this.taskContentCodec = taskContentCodec;
    }

    public List<WorkerRequestedAction> resolve(Task task, DocumentRequestDraft draft) {
        Map<String, WorkerRequestedAction> actions = new LinkedHashMap<>();
        requestedFields(task).forEach((key, sourceHint) -> definition(key, sourceHint)
                .ifPresent(definition -> actions.put(
                        "answer:" + key,
                        WorkerRequestedAction.answer(key, definition.label(), definition.inputType())
                )));
        draft.documentTypes().forEach(documentType -> actions.put(
                "upload:" + documentType.name(),
                WorkerRequestedAction.upload(documentType)
        ));
        return List.copyOf(actions.values());
    }

    public Map<String, String> validateAnswers(
            Map<String, String> answers,
            List<WorkerRequestedAction> requestedActions
    ) {
        if (answers == null || answers.isEmpty()) {
            return Map.of();
        }
        Map<String, WorkerRequestedAction> allowed = new LinkedHashMap<>();
        requestedActions.stream()
                .filter(action -> action.type() == WorkerRequestedActionType.ANSWER_FIELD)
                .forEach(action -> allowed.put(action.fieldKey(), action));
        Map<String, String> normalized = new LinkedHashMap<>();
        answers.forEach((key, value) -> {
            WorkerRequestedAction action = allowed.get(key);
            if (action == null || SENSITIVE_FIELDS.contains(key)) {
                throw new InvalidWorkerSlotAnswerException();
            }
            normalized.put(key, normalize(value, action.inputType()));
        });
        return Map.copyOf(normalized);
    }

    private Optional<FieldDefinition> definition(String key, String sourceHint) {
        if (key == null || SENSITIVE_FIELDS.contains(key)) {
            return Optional.empty();
        }
        FieldDefinition definition = WORKER_FIELDS.get(key);
        if (definition == null) {
            return Optional.empty();
        }
        if (!"WORKER_INPUT".equals(sourceHint)
                && !("USER_INPUT".equals(sourceHint) && "lodging".equals(key))) {
            return Optional.empty();
        }
        return Optional.of(definition);
    }

    private Map<String, String> requestedFields(Task task) {
        Map<String, Object> businessData = taskContentCodec.decodeBusinessData(task.businessDataJson());
        Object executionValue = businessData.get("renewal_execution");
        if (!(executionValue instanceof Map<?, ?> execution)) {
            return Map.of();
        }
        Object fieldsValue = execution.get("requested_fields");
        if (!(fieldsValue instanceof Iterable<?> fields)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Object fieldValue : fields) {
            if (!(fieldValue instanceof Map<?, ?> field)) {
                continue;
            }
            Object key = field.get("key");
            Object sourceHint = field.get("source_hint");
            if (key instanceof String fieldKey && sourceHint instanceof String source) {
                result.put(fieldKey, source);
            }
        }
        return Map.copyOf(result);
    }

    private String normalize(String value, WorkerRequestedActionInputType inputType) {
        if (value == null || value.isBlank()) {
            throw new InvalidWorkerSlotAnswerException();
        }
        String normalized = value.trim();
        return switch (inputType) {
            case TEXT -> normalizeText(normalized);
            case BOOLEAN -> normalizeBoolean(normalized);
            case MONEY -> normalizeMoney(normalized);
        };
    }

    private String normalizeText(String value) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new InvalidWorkerSlotAnswerException();
        }
        return value;
    }

    private String normalizeBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "예".equals(value)) {
            return "true";
        }
        if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "아니요".equals(value)) {
            return "false";
        }
        throw new InvalidWorkerSlotAnswerException();
    }

    private String normalizeMoney(String value) {
        try {
            BigDecimal money = new BigDecimal(value.replace(",", ""));
            if (money.signum() < 0 || money.scale() > 0 || money.compareTo(MAX_MONEY) > 0) {
                throw new InvalidWorkerSlotAnswerException();
            }
            return money.toPlainString();
        } catch (NumberFormatException exception) {
            throw new InvalidWorkerSlotAnswerException();
        }
    }

    public static final class InvalidWorkerSlotAnswerException extends RuntimeException {
    }

    private record FieldDefinition(String label, WorkerRequestedActionInputType inputType) {
    }
}
