package com.fowoco.server.task.application.renewal;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.task.application.error.TaskErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
final class RenewalSlotAnswerValidator {

    private static final int MAX_ANSWER_LENGTH = 2_000;
    private static final BigDecimal MAX_WAGE = new BigDecimal("999999999999");
    private static final Pattern SLOT_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Set<String> SENSITIVE_SLOTS = Set.of(
            "passport_number",
            "alien_registration_number",
            "date_of_birth",
            "full_name",
            "legal_name",
            "phone",
            "account_number"
    );
    private static final Set<String> DATE_SLOTS = Set.of(
            "contract_start_date",
            "contract_end_date",
            "stay_expiry_date"
    );

    Map<String, String> validate(Map<String, String> answers, Object previousExecution) {
        if (answers == null || answers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> requestedSources = requestedSources(previousExecution);
        Map<String, String> normalized = new LinkedHashMap<>();
        answers.forEach((key, value) -> {
            if (key == null
                    || !SLOT_KEY.matcher(key).matches()
                    || SENSITIVE_SLOTS.contains(key)
                    || !"USER_INPUT".equals(requestedSources.get(key))) {
                throw rejected();
            }
            String normalizedValue = normalizeValue(key, value);
            normalized.put(key, normalizedValue);
        });
        return Map.copyOf(normalized);
    }

    private String normalizeValue(String key, String value) {
        if (value == null || value.isBlank()) {
            throw rejected();
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_ANSWER_LENGTH) {
            throw rejected();
        }
        if ("wage".equals(key)) {
            return normalizeWage(normalized);
        }
        if ("working_hours".equals(key)) {
            return normalizeWorkingHours(normalized);
        }
        if (DATE_SLOTS.contains(key)) {
            return normalizeDate(normalized);
        }
        return normalized;
    }

    private String normalizeWage(String value) {
        String canonical = value.replace(",", "");
        try {
            BigDecimal wage = new BigDecimal(canonical);
            if (wage.signum() <= 0 || wage.scale() > 0 || wage.compareTo(MAX_WAGE) > 0) {
                throw rejected();
            }
            return wage.toPlainString();
        } catch (NumberFormatException exception) {
            throw rejected();
        }
    }

    private String normalizeWorkingHours(String value) {
        try {
            int hours = Integer.parseInt(value);
            if (hours < 1 || hours > 168) {
                throw rejected();
            }
            return Integer.toString(hours);
        } catch (NumberFormatException exception) {
            throw rejected();
        }
    }

    private String normalizeDate(String value) {
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException exception) {
            throw rejected();
        }
    }

    private Map<String, String> requestedSources(Object previousExecution) {
        if (!(previousExecution instanceof Map<?, ?> execution)) {
            return Map.of();
        }
        Object requestedFields = execution.get("requested_fields");
        if (!(requestedFields instanceof Iterable<?> fields)) {
            return Map.of();
        }
        Map<String, String> sources = new LinkedHashMap<>();
        fields.forEach(field -> {
            if (field instanceof Map<?, ?> requestedField) {
                Object key = requestedField.get("key");
                Object source = requestedField.get("source_hint");
                if (key instanceof String stringKey && source instanceof String stringSource) {
                    sources.put(stringKey, stringSource);
                }
            }
        });
        return Map.copyOf(sources);
    }

    private ApiException rejected() {
        return new ApiException(TaskErrorCode.INVALID_RENEWAL_SLOT_ANSWER);
    }
}
