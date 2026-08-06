package com.fowoco.server.aiintegration.application.ocr;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts the Server's ISO 3166-1 alpha-2 Worker nationality to the alpha-3
 * routing key used by the currently deployed AI passport templates.
 */
public final class AiOcrPassportCountryCodeResolver {

    private static final Pattern ALPHA_2 = Pattern.compile("[A-Z]{2}");
    private static final Pattern ALPHA_3 = Pattern.compile("[A-Z]{3}");
    private static final Map<String, String> WORKER_TO_OCR_COUNTRY = Map.of(
            "KR", "KOR",
            "PH", "PHL",
            "JP", "JPN",
            "CN", "CHN",
            "VN", "VNM"
    );
    private static final Set<String> SUPPORTED_OCR_COUNTRIES =
            Set.copyOf(WORKER_TO_OCR_COUNTRY.values());

    public String fromWorkerNationalityCode(String workerNationalityCode) {
        if (workerNationalityCode == null) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "Worker nationality code is missing.");
        }
        String normalized = workerNationalityCode.strip().toUpperCase(Locale.ROOT);
        if (!ALPHA_2.matcher(normalized).matches()) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "Worker nationality code is invalid.");
        }
        String ocrCountryCode = WORKER_TO_OCR_COUNTRY.get(normalized);
        if (ocrCountryCode == null) {
            reject(AiRuntimeFailureCode.UNSUPPORTED_OCR_COUNTRY, "Passport OCR country is unsupported.");
        }
        return ocrCountryCode;
    }

    public boolean isSupportedOcrCountryCode(String countryCode) {
        return countryCode != null
                && ALPHA_3.matcher(countryCode).matches()
                && SUPPORTED_OCR_COUNTRIES.contains(countryCode);
    }

    private void reject(AiRuntimeFailureCode code, String safeMessage) {
        throw new AiRuntimeContractException(code, safeMessage);
    }
}
