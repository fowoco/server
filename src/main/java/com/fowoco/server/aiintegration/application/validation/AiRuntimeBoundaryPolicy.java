package com.fowoco.server.aiintegration.application.validation;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Allows original demo data while preventing service credentials from crossing the AI boundary.
 */
@Component
public class AiRuntimeBoundaryPolicy {

    private static final Set<String> FORBIDDEN_KEY_PARTS = Set.of(
            "token",
            "password",
            "secret",
            "authorization",
            "prompt",
            "apikey",
            "토큰",
            "인증",
            "비밀",
            "api키",
            "비밀번호"
    );
    private static final Pattern BEARER_TOKEN =
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern JWT =
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(api[_-]?key|password|secret|token)\\s*[:=]\\s*\\S+"
    );

    public void validateText(String value, int maxLength, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "Required AI contract text is missing.");
            }
            return;
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        if (normalized.length() > maxLength) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI contract text exceeds its size limit.");
        }
        if (containsCredential(normalized)) {
            reject(
                    AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED,
                    "Service credential was rejected at the AI boundary."
            );
        }
    }

    public void validateKey(String key) {
        if (key == null || key.isBlank()) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI contract key is missing.");
        }
        String normalizedKey = Normalizer.normalize(key, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        boolean forbidden = FORBIDDEN_KEY_PARTS.stream()
                .map(part -> part.replace("_", "").replace("-", ""))
                .anyMatch(normalizedKey::contains);
        if (forbidden) {
            reject(
                    AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED,
                    "Service credential key was rejected at the AI boundary."
            );
        }
    }

    private boolean containsCredential(String value) {
        return BEARER_TOKEN.matcher(value).find()
                || JWT.matcher(value).find()
                || SECRET_ASSIGNMENT.matcher(value).find();
    }

    private void reject(AiRuntimeFailureCode failureCode, String safeMessage) {
        throw new AiRuntimeContractException(failureCode, safeMessage);
    }
}
