package com.fowoco.server.aiintegration.application.validation;

import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.ATTEMPT_ID;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.CONTRACT_VERSION;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.KNOWLEDGE_VERSION;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.REQUEST_ID;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.WORKER_REF;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.WORKFLOW_ID;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.contextRequiredResponse;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.needsInfoResponse;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.responseWithCandidate;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validCandidate;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validRequest;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validPlanRequest;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validResponse;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validVersions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisPhase;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiCandidate;
import com.fowoco.server.aiintegration.application.model.AiConfidenceSource;
import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.AiIntentDecision;
import com.fowoco.server.aiintegration.application.model.AiRuntimeVersions;
import com.fowoco.server.aiintegration.application.model.AnalysisInput;
import com.fowoco.server.aiintegration.application.model.WorkerContext;
import com.fowoco.server.aiintegration.application.model.WorkflowConstraint;
import com.fowoco.server.aiintegration.support.AiRuntimeContractFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Test
    void acceptsAnalyzeWithoutProviderAttemptWhenPlanDecisionIsReused() {
        AiAnalysisResponse valid = validResponse();
        AiAnalysisResponse withoutProviderCall = new AiAnalysisResponse(
                valid.requestId(),
                valid.outcome(),
                valid.contextRequirement(),
                valid.questions(),
                valid.candidates(),
                valid.validationErrors(),
                valid.versions(),
                0,
                valid.latencyMs()
        );

        assertThatCode(() -> validator.validateResponse(validRequest(), withoutProviderCall))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsInstructionOnlyPlanAndStructuredContextRequirement() {
        assertThatCode(() -> validator.validateResponse(validPlanRequest(), contextRequiredResponse()))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMissingEvidenceWhenTheFinalModelDoesNotProvideIt() {
        AiContextRequirement valid = contextRequiredResponse().contextRequirement();
        AiContextRequirement withoutEvidence = new AiContextRequirement(
                valid.detectedIntent(),
                valid.confidence(),
                valid.targetDisplayName(),
                valid.extractedSlots(),
                valid.requiredFieldKeys(),
                valid.workflowId(),
                null,
                valid.confidenceSource(),
                valid.bertRoutingScore()
        );
        AiAnalysisResponse response = new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.CONTEXT_REQUIRED,
                withoutEvidence,
                List.of(),
                List.of(),
                List.of(),
                validVersions(),
                1,
                100
        );

        assertThatCode(() -> validator.validateResponse(validPlanRequest(), response))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEvidenceOutsideTheOriginalInstruction() {
        AiContextRequirement valid = contextRequiredResponse().contextRequirement();
        AiContextRequirement inventedEvidence = new AiContextRequirement(
                valid.detectedIntent(),
                valid.confidence(),
                valid.targetDisplayName(),
                valid.extractedSlots(),
                valid.requiredFieldKeys(),
                valid.workflowId(),
                "원문에 없는 판단 근거",
                valid.confidenceSource(),
                valid.bertRoutingScore()
        );
        AiAnalysisResponse response = new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.CONTEXT_REQUIRED,
                inventedEvidence,
                List.of(),
                List.of(),
                List.of(),
                validVersions(),
                1,
                100
        );

        assertFailure(
                () -> validator.validateResponse(validPlanRequest(), response),
                AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT
        );
    }

    @Test
    void rejectsAConfidenceValueWhenTheFinalModelDoesNotProvideOne() {
        AiContextRequirement valid = contextRequiredResponse().contextRequirement();
        AiContextRequirement invalid = new AiContextRequirement(
                valid.detectedIntent(),
                new BigDecimal("0.3088"),
                valid.targetDisplayName(),
                valid.extractedSlots(),
                valid.requiredFieldKeys(),
                valid.workflowId(),
                valid.evidence(),
                AiConfidenceSource.UNAVAILABLE,
                new BigDecimal("0.3088")
        );
        AiAnalysisResponse response = new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.CONTEXT_REQUIRED,
                invalid,
                List.of(),
                List.of(),
                List.of(),
                validVersions(),
                1,
                100
        );

        assertFailure(
                () -> validator.validateResponse(validPlanRequest(), response),
                AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT
        );
    }

    @Test
    void acceptsQuestionsAsAValidBusinessOutcome() {
        assertThatCode(() -> validator.validateResponse(validRequest(), needsInfoResponse()))
                .doesNotThrowAnyException();
    }

    @Test
    void keepsPlanAndAnalyzeInputsSeparated() {
        AiAnalysisRequest planWithDatabaseContext = requestWithPhase(
                AiAnalysisPhase.PLAN,
                validRequest().analysisInput()
        );
        AiAnalysisRequest analyzeWithoutDatabaseContext = requestWithPhase(
                AiAnalysisPhase.ANALYZE,
                validPlanRequest().analysisInput()
        );

        assertFailure(
                () -> validator.validateRequest(planWithDatabaseContext),
                AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT
        );
        assertFailure(
                () -> validator.validateRequest(analyzeWithoutDatabaseContext),
                AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT
        );
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
                null,
                List.of(),
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
                null
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
                null
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
                null
        );
        assertFailure(
                () -> validator.validateResponse(validRequest(), responseWithCandidate(unknownSlot)),
                AiRuntimeFailureCode.UNEXPECTED_SLOT
        );
    }

    @Test
    void rejectsCandidateThatChangesTheWorkflowSelectedDuringPlan() {
        AiAnalysisRequest base = validRequest();
        AnalysisInput input = new AnalysisInput(
                base.analysisInput().instruction(),
                base.analysisInput().extractedSlots(),
                base.analysisInput().requestedFieldKeys(),
                base.analysisInput().workers(),
                List.of(
                        new WorkflowConstraint(WORKFLOW_ID, Set.of("stay_expiry_date")),
                        new WorkflowConstraint("WF-CON-001", Set.of("contract_end_date"))
                ),
                base.analysisInput().plannedIntentDecision()
        );
        AiAnalysisRequest request = new AiAnalysisRequest(
                base.requestId(),
                base.attemptId(),
                base.phase(),
                base.contractVersion(),
                base.requiredKnowledgeVersion(),
                base.deadlineMs(),
                input
        );
        AiCandidate changedWorkflow = new AiCandidate(
                "candidate-changed-workflow",
                WORKER_REF,
                "WF-CON-001",
                Map.of(),
                List.of("contract_end_date"),
                null
        );

        assertFailure(
                () -> validator.validateResponse(request, responseWithCandidate(changedWorkflow)),
                AiRuntimeFailureCode.UNEXPECTED_WORKFLOW
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
                null
        );

        assertThatCode(() -> validator.validateResponse(
                validRequest(),
                responseWithCandidate(originalValueCandidate)
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsCandidateThatChangesServerOwnedStayExpiryDate() {
        AiCandidate changedDate = new AiCandidate(
                "candidate-changed-date",
                WORKER_REF,
                WORKFLOW_ID,
                Map.of("stay_expiry_date", "2099-01-01"),
                List.of("contract_end_date", "monthly_wage"),
                null
        );

        assertFailure(
                () -> validator.validateResponse(validRequest(), responseWithCandidate(changedDate)),
                AiRuntimeFailureCode.CORE_VALUE_MISMATCH
        );
    }

    @Test
    void rejectsCandidateThatChangesServerOwnedDocumentStatus() {
        AiAnalysisRequest request = requestWithDocumentStatuses();
        AiCandidate changedStatus = new AiCandidate(
                "candidate-document-status",
                WORKER_REF,
                WORKFLOW_ID,
                Map.of(
                        "passport_status", "VERIFIED",
                        "arc_status", "VERIFIED"
                ),
                List.of(),
                null
        );

        assertFailure(
                () -> validator.validateResponse(request, responseWithCandidate(changedStatus)),
                AiRuntimeFailureCode.CORE_VALUE_MISMATCH
        );
    }

    @Test
    void acceptsCandidateThatPreservesServerOwnedDocumentStatuses() {
        AiCandidate preservedStatuses = new AiCandidate(
                "candidate-preserved-document-status",
                WORKER_REF,
                WORKFLOW_ID,
                Map.of(
                        "passport_status", "VERIFIED",
                        "arc_status", "MISSING"
                ),
                List.of(),
                null
        );

        assertThatCode(() -> validator.validateResponse(
                requestWithDocumentStatuses(),
                responseWithCandidate(preservedStatuses)
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsCandidateConfidenceThatDiffersFromPlan() {
        AiCandidate changedConfidence = new AiCandidate(
                "candidate-confidence",
                WORKER_REF,
                WORKFLOW_ID,
                Map.of(),
                List.of(),
                new BigDecimal("0.99")
        );

        assertFailure(
                () -> validator.validateResponse(validRequest(), responseWithCandidate(changedConfidence)),
                AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT
        );
    }

    @Test
    void acceptsCandidateThatPreservesBertPlanConfidence() {
        BigDecimal confidence = new BigDecimal("0.8400");
        AiAnalysisRequest request = requestWithPlanDecision(
                new AiIntentDecision(
                        "EXPIRY_RENEWAL",
                        WORKFLOW_ID,
                        null,
                        confidence,
                        AiConfidenceSource.BERT,
                        confidence
                )
        );
        AiCandidate candidate = new AiCandidate(
                "candidate-bert",
                WORKER_REF,
                WORKFLOW_ID,
                Map.of(),
                List.of(),
                new BigDecimal("0.84")
        );

        assertThatCode(() -> validator.validateResponse(request, responseWithCandidate(candidate)))
                .doesNotThrowAnyException();
    }

    private AiAnalysisRequest requestWithDocumentStatuses() {
        AiAnalysisRequest base = validRequest();
        WorkerContext original = base.analysisInput().workers().get(0);
        WorkerContext worker = new WorkerContext(
                original.workerRef(),
                original.displayName(),
                original.nationalityCode(),
                original.preferredLanguage(),
                original.workStatus(),
                original.stayExpiryDate(),
                original.contractStartDate(),
                original.contractEndDate(),
                Map.of(
                        "passport_status", "VERIFIED",
                        "arc_status", "MISSING"
                )
        );
        AnalysisInput input = new AnalysisInput(
                base.analysisInput().instruction(),
                base.analysisInput().extractedSlots(),
                List.of("passport_status", "arc_status"),
                List.of(worker),
                List.of(new WorkflowConstraint(
                        WORKFLOW_ID,
                        Set.of("passport_status", "arc_status")
                )),
                base.analysisInput().plannedIntentDecision()
        );
        return new AiAnalysisRequest(
                base.requestId(),
                base.attemptId(),
                base.phase(),
                base.contractVersion(),
                base.requiredKnowledgeVersion(),
                base.deadlineMs(),
                input
        );
    }

    private AiAnalysisResponse responseWithVersions(AiRuntimeVersions versions) {
        return new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.REVIEW_REQUIRED,
                null,
                List.of(),
                List.of(validCandidate()),
                List.of(),
                versions,
                1,
                100
        );
    }

    private AiAnalysisRequest requestWithPhase(
            AiAnalysisPhase phase,
            AnalysisInput input
    ) {
        return new AiAnalysisRequest(
                REQUEST_ID,
                ATTEMPT_ID,
                phase,
                CONTRACT_VERSION,
                KNOWLEDGE_VERSION,
                10_000,
                input
        );
    }

    private AiAnalysisRequest requestWithPlanDecision(AiIntentDecision decision) {
        AiAnalysisRequest base = validRequest();
        AnalysisInput input = new AnalysisInput(
                base.analysisInput().instruction(),
                base.analysisInput().extractedSlots(),
                base.analysisInput().requestedFieldKeys(),
                base.analysisInput().workers(),
                base.analysisInput().workflowConstraints(),
                decision
        );
        return new AiAnalysisRequest(
                base.requestId(),
                base.attemptId(),
                base.phase(),
                base.contractVersion(),
                base.requiredKnowledgeVersion(),
                base.deadlineMs(),
                input
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
