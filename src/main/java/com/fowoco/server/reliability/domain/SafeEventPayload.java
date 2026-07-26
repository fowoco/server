package com.fowoco.server.reliability.domain;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Event payload that accepts only explicitly allowed, non-sensitive fields and scalar values.
 */
public final class SafeEventPayload {

    private static final Pattern FIELD_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,49}$");
    private static final int MAX_COLLECTION_SIZE = 50;
    private static final int MAX_STRING_LENGTH = 500;
    private static final Pattern REGISTRATION_NUMBER =
            Pattern.compile("(?<!\\d)\\d{6}-?[1-8]\\d{6}(?!\\d)");
    private static final Pattern PHONE_NUMBER =
            Pattern.compile("(?<!\\d)01\\d[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)");
    private static final Pattern BEARER_TOKEN =
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(api[_-]?key|password|secret|token)\\s*[:=]\\s*\\S+"
    );
    private static final Set<String> SENSITIVE_NAME_PARTS = Set.of(
            "account_number",
            "alien_registration",
            "bank_account",
            "display_name",
            "email",
            "legal_name",
            "passport",
            "password",
            "phone",
            "prompt",
            "resident_number",
            "token",
            "jwt"
    );

    private final Map<String, Object> values;

    private SafeEventPayload(Map<String, Object> values) {
        this.values = values;
    }

    public static SafeEventPayload empty() {
        return new SafeEventPayload(Map.of());
    }

    public static SafeEventPayload of(
            Set<String> allowedFields,
            Map<String, ?> rawValues
    ) {
        Objects.requireNonNull(allowedFields, "allowedFields must not be null");
        Objects.requireNonNull(rawValues, "rawValues must not be null");
        Set<String> immutableAllowedFields = Set.copyOf(allowedFields);
        immutableAllowedFields.forEach(SafeEventPayload::validateFieldName);

        Map<String, Object> safeValues = new LinkedHashMap<>();
        rawValues.forEach((field, value) -> {
            validateFieldName(field);
            if (!immutableAllowedFields.contains(field)) {
                throw new IllegalArgumentException(
                        "Event payload field is not allow-listed: " + field
                );
            }
            if (value != null) {
                safeValues.put(field, canonicalize(value));
            }
        });
        return new SafeEventPayload(Collections.unmodifiableMap(safeValues));
    }

    public Map<String, Object> values() {
        return values;
    }

    private static void validateFieldName(String field) {
        if (field == null || !FIELD_NAME.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid event payload field name.");
        }
        String normalized = field.toLowerCase(Locale.ROOT);
        if (SENSITIVE_NAME_PARTS.stream().anyMatch(normalized::contains)) {
            throw new IllegalArgumentException(
                    "Sensitive event payload field is not allowed: " + field
            );
        }
    }

    private static Object canonicalize(Object value) {
        if (value instanceof String text) {
            if (text.length() > MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("Event payload string is too long.");
            }
            if (containsSensitiveValue(text)) {
                throw new IllegalArgumentException(
                        "Sensitive event payload value is not allowed."
                );
            }
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof UUID || value instanceof TemporalAccessor) {
            return value.toString();
        }
        if (value instanceof Collection<?> collection) {
            if (collection.size() > MAX_COLLECTION_SIZE) {
                throw new IllegalArgumentException("Event payload collection is too large.");
            }
            List<Object> safeItems = new ArrayList<>(collection.size());
            collection.forEach(item -> {
                if (item == null || item instanceof Map<?, ?> || item instanceof Collection<?>) {
                    throw new IllegalArgumentException(
                            "Event payload collections support non-null scalar items only."
                    );
                }
                safeItems.add(canonicalize(item));
            });
            return List.copyOf(safeItems);
        }
        throw new IllegalArgumentException(
                "Unsupported event payload value type: " + value.getClass().getSimpleName()
        );
    }

    private static boolean containsSensitiveValue(String value) {
        return REGISTRATION_NUMBER.matcher(value).find()
                || PHONE_NUMBER.matcher(value).find()
                || BEARER_TOKEN.matcher(value).find()
                || SECRET_ASSIGNMENT.matcher(value).find();
    }
}
