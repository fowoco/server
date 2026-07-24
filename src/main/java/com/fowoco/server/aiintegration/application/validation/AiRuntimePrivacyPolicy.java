package com.fowoco.server.aiintegration.application.validation;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Rejects sensitive values before they cross the AI boundary or enter an AiRun candidate.
 */
@Component
public class AiRuntimePrivacyPolicy {

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
    private static final Pattern LABELED_SENSITIVE_VALUE = Pattern.compile(
            "(?i)(passport[_ -]?(number|no)|alien[_ -]?registration[_ -]?(number|no)"
                    + "|bank[_ -]?account|여권번호|외국인등록번호|계좌번호)"
                    + "(?:은|는)?\\s*[:=]?\\s*[A-Za-z0-9-]{6,}"
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
        if (containsSensitiveValue(normalized)) {
            reject(AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED, "Sensitive data was rejected at the AI boundary.");
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
            reject(AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED, "Sensitive key was rejected at the AI boundary.");
        }
    }

    private boolean containsSensitiveValue(String value) {
        return REGISTRATION_NUMBER.matcher(value).find()
                || PHONE_NUMBER.matcher(value).find()
                || BEARER_TOKEN.matcher(value).find()
                || SECRET_ASSIGNMENT.matcher(value).find()
                || LABELED_SENSITIVE_VALUE.matcher(value).find();
    }

    private void reject(AiRuntimeFailureCode failureCode, String safeMessage) {
        throw new AiRuntimeContractException(failureCode, safeMessage);
    }
}
