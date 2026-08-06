package com.fowoco.server.aiintegration.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.ocr.AiOcrDocumentSide;
import com.fowoco.server.aiintegration.application.ocr.AiOcrDocumentType;
import com.fowoco.server.aiintegration.application.ocr.AiOcrFile;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.fowoco.server.aiintegration.application.ocr.AiOcrStatus;
import com.fowoco.server.aiintegration.support.FakeAiOcrClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidatingAiOcrClientTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID DOCUMENT_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
    );

    private final AiOcrContractValidator validator = new AiOcrContractValidator();

    @Test
    void validPassportContractIsDelegatedExactlyOnce() {
        FakeAiOcrClient fake = new FakeAiOcrClient();
        AiOcrRequest request = passportRequest();
        AiOcrResponse response = passportResponse(
                REQUEST_ID,
                Map.of("passport_number", "M12345678"),
                Map.of("passport_number", new BigDecimal("0.98"))
        );
        fake.enqueueResponse(response);

        AiOcrResponse actual = new ValidatingAiOcrClient(fake, validator)
                .recognize(request, AiRuntimeCallContext.withoutTrace());

        assertThat(actual).isEqualTo(response);
        assertThat(fake.receivedRequests()).containsExactly(request);
    }

    @Test
    void requestFileBytesAreDefensivelyCopied() {
        byte[] original = new byte[]{1, 2, 3};
        AiOcrFile file = new AiOcrFile("passport.png", "image/png", original);

        original[0] = 9;
        byte[] returned = file.content();
        returned[1] = 9;

        assertThat(file.content()).containsExactly(1, 2, 3);
    }

    @Test
    void invalidRequestIsRejectedBeforeRuntimeCall() {
        FakeAiOcrClient fake = new FakeAiOcrClient();
        AiOcrRequest request = new AiOcrRequest(
                REQUEST_ID,
                DOCUMENT_ID,
                AiOcrDocumentType.PASSPORT_COPY,
                null,
                new AiOcrFile("passport.txt", "text/plain", new byte[]{1})
        );

        assertFailureCode(
                () -> new ValidatingAiOcrClient(fake, validator)
                        .recognize(request, AiRuntimeCallContext.withoutTrace()),
                AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT
        );
        assertThat(fake.receivedRequests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"THA", "NPL"})
    void countryWithoutADeployedTemplateIsRejectedBeforeRuntimeCall(String countryCode) {
        FakeAiOcrClient fake = new FakeAiOcrClient();
        AiOcrRequest request = new AiOcrRequest(
                REQUEST_ID,
                DOCUMENT_ID,
                AiOcrDocumentType.PASSPORT_COPY,
                countryCode,
                new AiOcrFile("passport.png", "image/png", new byte[]{1})
        );

        assertFailureCode(
                () -> new ValidatingAiOcrClient(fake, validator)
                        .recognize(request, AiRuntimeCallContext.withoutTrace()),
                AiRuntimeFailureCode.UNSUPPORTED_OCR_COUNTRY
        );
        assertThat(fake.receivedRequests()).isEmpty();
    }

    @Test
    void responseRequestIdMismatchIsRejected() {
        FakeAiOcrClient fake = new FakeAiOcrClient();
        fake.enqueueResponse(passportResponse(
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                Map.of("passport_number", "M12345678"),
                Map.of("passport_number", new BigDecimal("0.98"))
        ));

        assertFailureCode(
                () -> new ValidatingAiOcrClient(fake, validator)
                        .recognize(passportRequest(), AiRuntimeCallContext.withoutTrace()),
                AiRuntimeFailureCode.REQUEST_ID_MISMATCH
        );
    }

    @Test
    void unknownFieldAndConfidenceMismatchAreRejected() {
        FakeAiOcrClient fake = new FakeAiOcrClient();
        fake.enqueueResponse(passportResponse(
                REQUEST_ID,
                Map.of("raw_provider_response", "secret"),
                Map.of()
        ));

        assertFailureCode(
                () -> new ValidatingAiOcrClient(fake, validator)
                        .recognize(passportRequest(), AiRuntimeCallContext.withoutTrace()),
                AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT
        );
    }

    @Test
    void reviewRequiredMustIncludeSafeReason() {
        FakeAiOcrClient fake = new FakeAiOcrClient();
        fake.enqueueResponse(new AiOcrResponse(
                REQUEST_ID,
                DOCUMENT_ID,
                AiOcrStatus.REVIEW_REQUIRED,
                43019L,
                AiOcrDocumentSide.FRONT,
                Map.of(),
                Map.of(),
                List.of()
        ));

        assertFailureCode(
                () -> new ValidatingAiOcrClient(fake, validator)
                        .recognize(passportRequest(), AiRuntimeCallContext.withoutTrace()),
                AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT
        );
    }

    private AiOcrRequest passportRequest() {
        return new AiOcrRequest(
                REQUEST_ID,
                DOCUMENT_ID,
                AiOcrDocumentType.PASSPORT_COPY,
                "VNM",
                new AiOcrFile("passport.png", "image/png", new byte[]{1, 2, 3})
        );
    }

    private AiOcrResponse passportResponse(
            UUID requestId,
            Map<String, String> fields,
            Map<String, BigDecimal> confidences
    ) {
        return new AiOcrResponse(
                requestId,
                DOCUMENT_ID,
                AiOcrStatus.SUCCEEDED,
                43019L,
                null,
                fields,
                confidences,
                List.of()
        );
    }

    private void assertFailureCode(Runnable action, AiRuntimeFailureCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AiRuntimeContractException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(expected));
    }
}
