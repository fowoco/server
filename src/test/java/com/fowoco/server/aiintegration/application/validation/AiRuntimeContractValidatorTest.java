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
            new AiRuntimeContractValidator(new AiRuntimeBoundaryPolicy());

    @Test
    void acceptsValidRequestAndResponse() {
        assertThatCode(() -> validator.validateResponse(validRequest(), validResponse()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("credentialInstructions")
    void rejectsServiceCredentialsBeforeOutboundCall(String instruction) {
        assertFailure(
                () -> validator.validateRequest(AiRuntimeContractFixture.requestWithInstruction(instruction)),
                AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED
        );
    }

    static Stream<String> credentialInstructions() {
        return Stream.of(
                "Authorization: Bearer secret-token-value",
                "api_key=do-not-send-this",
                "JWT eyJ12345678.abcdefgh12345678.signature12345678"
        );
    }

    @ParameterizedTest
    @MethodSource("forbiddenCredentialKeys")
    void rejectsServiceCredentialKeys(String fieldKey) {
        assertFailure(
                () -> new AiRuntimeBoundaryPolicy().validateKey(fieldKey),
                AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED
        );
    }

    static Stream<String> forbiddenCredentialKeys() {
        return Stream.of(
                "access_token",
                "authorization",
                "password",
                "api_key",
                "service_secret"
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
                AiRuntimeFailureCode.UNEXPECTED_SLOT
        );
    }

    @Test
    void acceptsOriginalPiiCandidateValueWhenTheWorkflowAllowsTheSlot() {
        AiCandidate originalValueCandidate = new AiCandidate(
                "candidate-original-value",
                WORKER_REF,
                WORKFLOW_ID,
                Map.of("contract_end_date", "담당자 전화 010-1234-5678"),
                List.of(),
                BigDecimal.ONE
        );

        assertThatCode(() -> validator.validateResponse(
                validRequest(),
                responseWithCandidate(originalValueCandidate)
        )).doesNotThrowAnyException();
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
