package com.fowoco.server.aiintegration.application.validation;

import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.CONTRACT_VERSION;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.KNOWLEDGE_VERSION;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.REQUEST_ID;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.WORKER_REF;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.WORKFLOW_ID;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.responseWithCandidate;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validCandidate;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validRequest;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validResponse;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validVersions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiCandidate;
import com.fowoco.server.aiintegration.application.model.AiRuntimeVersions;
import com.fowoco.server.aiintegration.support.AiRuntimeContractFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AiRuntimeContractValidatorTest {

    private final AiRuntimeContractValidator validator =
            new AiRuntimeContractValidator(new AiRuntimePrivacyPolicy());

    @Test
    void acceptsValidRequestAndResponse() {
        assertThatCode(() -> validator.validateResponse(validRequest(), validResponse()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("sensitiveInstructions")
    void rejectsSensitiveInstructionBeforeOutboundCall(String instruction) {
        assertFailure(
                () -> validator.validateRequest(AiRuntimeContractFixture.requestWithInstruction(instruction)),
                AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED
        );
    }

    static Stream<String> sensitiveInstructions() {
        return Stream.of(
                "연락처는 010-1234-5678입니다",
                "외국인등록번호 990101-5123456",
                "passport_number: M12345678",
                "Authorization: Bearer secret-token-value",
                "api_key=do-not-send-this"
        );
    }

    @Test
    void rejectsMismatchedRequestIdWithoutLeakingRawResponse() {
        AiAnalysisResponse response = new AiAnalysisResponse(
                UUID.randomUUID(),
                AiAnalysisOutcome.REVIEW_REQUIRED,
                validResponse().candidates(),
                List.of(),
                validVersions(),
                1,
                100
        );

        assertFailure(
                () -> validator.validateResponse(validRequest(), response),
                AiRuntimeFailureCode.REQUEST_ID_MISMATCH
        );
    }

    @Test
    void rejectsContractAndKnowledgeVersionDrift() {
        AiRuntimeVersions wrongContract = versions(CONTRACT_VERSION + "-other", KNOWLEDGE_VERSION);
        assertFailure(
                () -> validator.validateResponse(validRequest(), responseWithVersions(wrongContract)),
                AiRuntimeFailureCode.CONTRACT_VERSION_MISMATCH
        );

        AiRuntimeVersions wrongKnowledge = versions(CONTRACT_VERSION, "9.9.9");
        assertFailure(
                () -> validator.validateResponse(validRequest(), responseWithVersions(wrongKnowledge)),
                AiRuntimeFailureCode.KNOWLEDGE_VERSION_MISMATCH
        );
    }

    @Test
    void rejectsCandidateOutsideRequestAllowList() {
        AiCandidate unknownWorker = new AiCandidate(
                "candidate-worker",
                UUID.randomUUID(),
                WORKFLOW_ID,
                Map.of(),
                List.of("stay_expiry_date"),
                BigDecimal.ONE
        );
        assertFailure(
                () -> validator.validateResponse(validRequest(), responseWithCandidate(unknownWorker)),
                AiRuntimeFailureCode.UNEXPECTED_WORKER_REFERENCE
        );

        AiCandidate unknownWorkflow = new AiCandidate(
                "candidate-workflow",
                WORKER_REF,
                "UNKNOWN_WORKFLOW",
                Map.of(),
                List.of(),
                BigDecimal.ONE
        );
        assertFailure(
                () -> validator.validateResponse(validRequest(), responseWithCandidate(unknownWorkflow)),
                AiRuntimeFailureCode.UNEXPECTED_WORKFLOW
        );

        AiCandidate unknownSlot = new AiCandidate(
                "candidate-slot",
                WORKER_REF,
                WORKFLOW_ID,
                Map.of("passport_number", "M12345678"),
                List.of(),
                BigDecimal.ONE
        );
        assertFailure(
                () -> validator.validateResponse(validRequest(), responseWithCandidate(unknownSlot)),
                AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED
        );
    }

    @Test
    void rejectsSensitiveCandidateValueAndKeepsExceptionMessageSafe() {
        AiCandidate sensitiveCandidate = new AiCandidate(
                "candidate-sensitive",
                WORKER_REF,
                WORKFLOW_ID,
                Map.of("contract_end_date", "담당자 전화 010-1234-5678"),
                List.of(),
                BigDecimal.ONE
        );

        assertThatThrownBy(() -> validator.validateResponse(
                validRequest(),
                responseWithCandidate(sensitiveCandidate)
        )).isInstanceOfSatisfying(AiRuntimeContractException.class, exception -> {
            assertThat(exception.failureCode()).isEqualTo(AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED);
            assertThat(exception.getMessage()).doesNotContain("010-1234-5678");
        });
    }

    private AiAnalysisResponse responseWithVersions(AiRuntimeVersions versions) {
        return new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.REVIEW_REQUIRED,
                List.of(validCandidate()),
                List.of(),
                versions,
                1,
                100
        );
    }

    private AiRuntimeVersions versions(String contractVersion, String knowledgeVersion) {
        AiRuntimeVersions valid = validVersions();
        return new AiRuntimeVersions(
                valid.agentVersion(),
                valid.modelProvider(),
                valid.modelName(),
                valid.modelVersion(),
                valid.promptVersion(),
                valid.contextPackVersion(),
                knowledgeVersion,
                contractVersion
        );
    }

    private void assertFailure(Runnable invocation, AiRuntimeFailureCode expectedCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(AiRuntimeContractException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(expectedCode)
                );
    }
}
