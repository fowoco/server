package com.fowoco.server.task.application;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.task.application.error.TaskErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class TaskContentCodec {

    private static final Set<String> FORBIDDEN_KEY_PARTS = Set.of(
            "passportnumber",
            "passportno",
            "alienregistrationnumber",
            "registrationnumber",
            "residentnumber",
            "rrn",
            "phone",
            "accountnumber",
            "bankaccount",
            "token",
            "password",
            "secret",
            "authorization",
            "prompt",
            "여권번호",
            "외국인등록번호",
            "주민등록번호",
            "전화",
            "계좌",
            "비밀번호"
    );
    private static final Pattern REGISTRATION_NUMBER =
            Pattern.compile("(?<!\\d)\\d{6}-?[1-8]\\d{6}(?!\\d)");
    private static final Pattern PHONE_NUMBER =
            Pattern.compile("(?<!\\d)01\\d[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)");
    private static final Pattern BEARER_TOKEN =
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(api[_-]?key|password|secret|token)\\s*[:=]\\s*\\S+"
    );

    private final ObjectMapper objectMapper;

    public TaskContentCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EncodedTaskContent encode(
            UUID workerId,
            String workflowId,
            String taskType,
            String title,
            String description,
            LocalDate dueDate,
            Map<String, Object> businessData
    ) {
        Object normalizedBusinessData = normalize(businessData == null ? Map.of() : businessData, null);
        TreeMap<String, Object> approvalTarget = new TreeMap<>();
        approvalTarget.put("business_data", normalizedBusinessData);
        approvalTarget.put("description", normalizeNullableText(description, 2000));
        approvalTarget.put("due_date", dueDate == null ? null : dueDate.toString());
        approvalTarget.put("task_type", normalizeText(taskType, 40));
        approvalTarget.put("title", normalizeText(title, 160));
        approvalTarget.put("worker_id", workerId.toString());
        approvalTarget.put("workflow_id", normalizeText(workflowId, 100));
        try {
            String businessDataJson = objectMapper.writeValueAsString(normalizedBusinessData);
            String canonicalTarget = objectMapper.writeValueAsString(approvalTarget);
            return new EncodedTaskContent(businessDataJson, sha256(canonicalTarget));
        } catch (JacksonException exception) {
            throw rejected();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> decodeBusinessData(String businessDataJson) {
        try {
            return objectMapper.readValue(businessDataJson, Map.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("저장된 Task business_data_json을 읽을 수 없습니다.", exception);
        }
    }

    public String safeText(String value, int maxLength) {
        return normalizeText(value, maxLength);
    }

    private Object normalize(Object value, String key) {
        if (key != null && containsForbiddenKey(key)) {
            throw rejected();
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> normalized = new TreeMap<>();
            map.forEach((mapKey, mapValue) -> {
                if (!(mapKey instanceof String stringKey) || stringKey.isBlank()) {
                    throw rejected();
                }
                normalized.put(stringKey, normalize(mapValue, stringKey));
            });
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            collection.forEach(item -> normalized.add(normalize(item, key)));
            return normalized;
        }
        if (value instanceof String string) {
            return normalizeText(string, 4000);
        }
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return normalize(objectMapper.convertValue(value, Object.class), key);
    }

    private String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw rejected();
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        if (normalized.length() > maxLength || containsSensitiveValue(normalized)) {
            throw rejected();
        }
        return normalized;
    }

    private String normalizeNullableText(String value, int maxLength) {
        return value == null || value.isBlank() ? null : normalizeText(value, maxLength);
    }

    private boolean containsForbiddenKey(String key) {
        String normalizedKey = Normalizer.normalize(key, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        return FORBIDDEN_KEY_PARTS.stream()
                .map(part -> part.replace("_", "").replace("-", ""))
                .anyMatch(normalizedKey::contains);
    }

    private boolean containsSensitiveValue(String value) {
        return REGISTRATION_NUMBER.matcher(value).find()
                || PHONE_NUMBER.matcher(value).find()
                || BEARER_TOKEN.matcher(value).find()
                || SECRET_ASSIGNMENT.matcher(value).find();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private ApiException rejected() {
        return new ApiException(TaskErrorCode.SENSITIVE_TASK_DATA_REJECTED);
    }

    public record EncodedTaskContent(String businessDataJson, String criticalFingerprint) {
    }
}
