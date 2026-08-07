package com.fowoco.server.aiintegration.application.validation;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.ocr.AiOcrDocumentSide;
import com.fowoco.server.aiintegration.application.ocr.AiOcrDocumentType;
import com.fowoco.server.aiintegration.application.ocr.AiOcrPassportCountryCodeResolver;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.fowoco.server.aiintegration.application.ocr.AiOcrStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class AiOcrContractValidator {

    public static final int MAX_FILE_BYTES = 20 * 1024 * 1024;

    private static final Set<String> CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf"
    );
    private static final Set<String> PASSPORT_FIELDS = Set.of(
            "passport_number",
            "surname",
            "given_names",
            "date_of_birth",
            "sex",
            "passport_issue_date",
            "passport_expiry_date"
    );
    private static final Set<String> ARC_FIELDS = Set.of(
            "alien_registration_number",
            "visa_type",
            "stay_expiration_date",
            "residence_address_1"
    );
    private static final Set<String> PASSPORT_REQUIRED_FIELDS = Set.of(
            "passport_number", "surname", "given_names", "date_of_birth", "passport_expiry_date"
    );
    private static final Set<String> DATE_FIELDS = Set.of(
            "date_of_birth", "passport_issue_date", "passport_expiry_date", "stay_expiration_date"
    );
    private static final Map<Long, AiOcrDocumentSide> ARC_TEMPLATE_SIDES = Map.of(
            43024L, AiOcrDocumentSide.FRONT,
            43025L, AiOcrDocumentSide.BACK
    );
    private static final Pattern SAFE_REASON = Pattern.compile("[a-z0-9_:-]{1,120}");

    private final AiOcrPassportCountryCodeResolver passportCountryCodeResolver =
            new AiOcrPassportCountryCodeResolver();

    public void validateRequest(AiOcrRequest request) {
        if (request == null) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "OCR request is missing.");
        }
        if (request.file().size() < 1 || request.file().size() > MAX_FILE_BYTES) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "OCR file size is invalid.");
        }
        if (!CONTENT_TYPES.contains(request.file().contentType())) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "OCR file type is not allowed.");
        }
        if (request.file().fileName().isBlank() || request.file().fileName().length() > 255) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "OCR file name is invalid.");
        }
        if (request.documentType() == AiOcrDocumentType.PASSPORT_COPY) {
            if (request.countryCode() == null || !request.countryCode().matches("[A-Z]{3}")) {
                reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "OCR country code is invalid.");
            }
            if (!passportCountryCodeResolver.isSupportedOcrCountryCode(request.countryCode())) {
                reject(AiRuntimeFailureCode.UNSUPPORTED_OCR_COUNTRY, "Passport OCR country is unsupported.");
            }
        } else if (request.countryCode() != null && !request.countryCode().isBlank()) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "ARC country code must be omitted.");
        }
    }

    public void validateResponse(AiOcrRequest request, AiOcrResponse response) {
        validateRequest(request);
        if (response == null) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR response is missing.");
        }
        if (!request.requestId().equals(response.requestId())) {
            reject(AiRuntimeFailureCode.REQUEST_ID_MISMATCH, "OCR response requestId does not match.");
        }
        if (!request.workerDocumentId().equals(response.workerDocumentId())) {
            reject(
                    AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT,
                    "OCR response workerDocumentId does not match."
            );
        }
        Set<String> allowedFields = request.documentType() == AiOcrDocumentType.PASSPORT_COPY
                ? PASSPORT_FIELDS
                : ARC_FIELDS;
        if (!allowedFields.containsAll(response.fields().keySet())
                || !response.fields().keySet().equals(response.fieldConfidences().keySet())) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR response fields are invalid.");
        }
        response.fields().forEach((key, value) -> {
            if (value == null || value.isBlank() || value.length() > 500) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR response field value is invalid.");
            }
            if (DATE_FIELDS.contains(key)) {
                validateIsoDate(value);
            }
        });
        response.fieldConfidences().forEach(this::validateConfidence);
        response.reviewReasons().forEach(reason -> {
            if (reason == null || !SAFE_REASON.matcher(reason).matches()) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR review reason is invalid.");
            }
        });
        validateOutcome(request, response);
    }

    private void validateConfidence(String key, BigDecimal confidence) {
        if (confidence == null
                || confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR confidence is invalid.");
        }
    }

    private void validateOutcome(AiOcrRequest request, AiOcrResponse response) {
        if (response.status() == AiOcrStatus.SUCCEEDED && !response.reviewReasons().isEmpty()) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Successful OCR cannot require review.");
        }
        if (response.status() == AiOcrStatus.REVIEW_REQUIRED && response.reviewReasons().isEmpty()) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR review reason is required.");
        }
        if (response.matchedTemplateId() != null && response.matchedTemplateId() < 1) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR template id is invalid.");
        }
        validateTemplate(request, response);
        if (response.status() == AiOcrStatus.SUCCEEDED) {
            validateSuccessfulFields(request, response);
        }
    }

    private void validateTemplate(AiOcrRequest request, AiOcrResponse response) {
        Long templateId = response.matchedTemplateId();
        if (templateId == null) {
            if (response.status() == AiOcrStatus.SUCCEEDED
                    || response.documentSide() != null
                    || !response.fields().isEmpty()) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR template result is inconsistent.");
            }
            return;
        }
        if (request.documentType() == AiOcrDocumentType.PASSPORT_COPY) {
            Long expected = passportCountryCodeResolver.expectedTemplateId(request.countryCode());
            if (!templateId.equals(expected)) {
                requireUnexpectedTemplateReview(response);
                return;
            }
            if (response.documentSide() != null) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Passport OCR side must be omitted.");
            }
            return;
        }
        AiOcrDocumentSide expectedSide = ARC_TEMPLATE_SIDES.get(templateId);
        if (expectedSide == null) {
            requireUnexpectedTemplateReview(response);
            return;
        }
        if (response.documentSide() != expectedSide) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "ARC OCR template side does not match.");
        }
    }

    private void requireUnexpectedTemplateReview(AiOcrResponse response) {
        if (response.status() != AiOcrStatus.REVIEW_REQUIRED
                || !response.reviewReasons().contains("unexpected_template")
                || response.documentSide() != null
                || !response.fields().isEmpty()) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR template does not match the request.");
        }
    }

    private void validateSuccessfulFields(AiOcrRequest request, AiOcrResponse response) {
        if (response.fields().isEmpty()) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Successful OCR fields are empty.");
        }
        if (request.documentType() == AiOcrDocumentType.PASSPORT_COPY) {
            if (!response.fields().keySet().containsAll(PASSPORT_REQUIRED_FIELDS)) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Passport OCR required fields are missing.");
            }
            return;
        }
        if (response.documentSide() == AiOcrDocumentSide.FRONT
                && !response.fields().containsKey("alien_registration_number")) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "ARC front required field is missing.");
        }
        if (response.documentSide() == AiOcrDocumentSide.BACK
                && response.fields().keySet().stream().noneMatch(
                        key -> key.startsWith("stay_") || key.startsWith("residence_")
                )) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "ARC back required field is missing.");
        }
    }

    private void validateIsoDate(String value) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "OCR date field is invalid.");
        }
    }

    private void reject(AiRuntimeFailureCode code, String safeMessage) {
        throw new AiRuntimeContractException(code, safeMessage);
    }
}
