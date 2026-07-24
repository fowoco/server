package com.fowoco.server.approval.application;

import com.fowoco.server.approval.application.error.ApprovalErrorCode;
import com.fowoco.server.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SafeJsonService {

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
    private static final Pattern REGISTRATION_NUMBER = Pattern.compile("(?<!\\d)\\d{6}-?[1-8]\\d{6}(?!\\d)");
    private static final Pattern PHONE_NUMBER = Pattern.compile("(?<!\\d)01\\d[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(api[_-]?key|password|secret|token)\\s*[:=]\\s*\\S+"
    );

    private final ObjectMapper objectMapper;

    public SafeJsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value, boolean required) {
        if (value == null) {
            if (required) {
                throw rejected();
            }
            return null;
        }
        Object normalized = normalize(value, null);
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JacksonException exception) {
            throw rejected();
        }
    }

    public String fingerprint(Object value) {
        String canonicalJson = write(value, true);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    public String safeText(String value, int maxLength, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw rejected();
            }
            return null;
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        if (normalized.length() > maxLength || containsSensitiveValue(normalized)) {
            throw rejected();
        }
        return normalized;
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
            String normalized = Normalizer.normalize(string, Normalizer.Form.NFKC);
            if (normalized.length() > 4000 || containsSensitiveValue(normalized)) {
                throw rejected();
            }
            return normalized;
        }
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return normalize(objectMapper.convertValue(value, Object.class), key);
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

    private ApiException rejected() {
        return new ApiException(ApprovalErrorCode.SENSITIVE_SNAPSHOT_REJECTED);
    }
}
