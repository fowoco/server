package com.fowoco.server.aiintegration.support;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisPhase;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiCandidate;
import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.AiConfidenceSource;
import com.fowoco.server.aiintegration.application.model.AiIntentDecision;
import com.fowoco.server.aiintegration.application.model.AiQuestion;
import com.fowoco.server.aiintegration.application.model.AiRuntimeVersions;
import com.fowoco.server.aiintegration.application.model.AnalysisInput;
import com.fowoco.server.aiintegration.application.model.WorkerContext;
import com.fowoco.server.aiintegration.application.model.WorkflowConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AiRuntimeContractFixture {

    public static final UUID REQUEST_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID ATTEMPT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    public static final UUID WORKER_REF = UUID.fromString("30000000-0000-0000-0000-000000000001");
    public static final String CONTRACT_VERSION = "1.0.0";
    public static final String KNOWLEDGE_VERSION = "0.2.0";
    public static final String INTENT = "EXPIRY_RENEWAL";
    public static final String WORKFLOW_ID = "WF-STY-001";

    private AiRuntimeContractFixture() {
    }

    public static AiAnalysisRequest validRequest() {
        return validAnalyzeRequest();
    }

    public static AiAnalysisRequest validPlanRequest() {
        return planRequestWithInstruction("응웬반안 체류연장 준비해줘");
    }

    public static AiAnalysisRequest planRequestWithInstruction(String instruction) {
        return new AiAnalysisRequest(
                REQUEST_ID,
                ATTEMPT_ID,
                AiAnalysisPhase.PLAN,
                CONTRACT_VERSION,
                KNOWLEDGE_VERSION,
                10_000,
                new AnalysisInput(instruction, Map.of(), List.of(), List.of(), List.of())
        );
    }

    public static AiAnalysisRequest validAnalyzeRequest() {
        return requestWithInstruction(
                "가상 근로자 응웬반안(010-1234-5678)의 체류연장 준비"
        );
    }

    public static AiAnalysisRequest requestWithInstruction(String instruction) {
        return new AiAnalysisRequest(
                REQUEST_ID,
                ATTEMPT_ID,
                AiAnalysisPhase.ANALYZE,
                CONTRACT_VERSION,
                KNOWLEDGE_VERSION,
                10_000,
                new AnalysisInput(
                        instruction,
                        Map.of("document_type", "STAY_EXTENSION"),
                        List.of(
                                "legal_name",
                                "passport_number",
                                "phone",
                                "email"
                        ),
                        List.of(new WorkerContext(
                                WORKER_REF,
                                "응웬반안",
                                "VN",
                                "vi",
                                "ACTIVE",
                                LocalDate.of(2026, 12, 31),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 12, 31),
                                Map.of(
                                        "legal_name", "NGUYEN VAN AN",
                                        "passport_number", "M12345678",
                                        "phone", "010-1234-5678",
                                        "email", "worker@example.com"
                                )
                        )),
                        List.of(new WorkflowConstraint(
                                WORKFLOW_ID,
                                Set.of("stay_expiry_date", "contract_end_date", "monthly_wage")
                        )),
                        new AiIntentDecision(
                                INTENT,
                                WORKFLOW_ID,
                                "체류연장 준비",
                                null,
                                AiConfidenceSource.UNAVAILABLE,
                                new BigDecimal("0.3088")
                        )
                )
        );
    }

    public static AiAnalysisResponse validResponse() {
        return responseWithCandidate(validCandidate());
    }

    public static AiAnalysisResponse contextRequiredResponse() {
        return new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.CONTEXT_REQUIRED,
                new AiContextRequirement(
                        INTENT,
                        null,
                        "응웬반안",
                        Map.of(),
                        List.of("legal_name", "stay_expiry_date"),
                        WORKFLOW_ID,
                        "체류연장 준비해줘",
                        AiConfidenceSource.UNAVAILABLE,
                        new BigDecimal("0.3088")
                ),
                List.of(),
                List.of(),
                List.of(),
                validVersions(),
                1,
                120
        );
    }

    public static AiAnalysisResponse needsInfoResponse() {
        return new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.NEEDS_INFO,
                null,
                List.of(new AiQuestion("monthly_wage", "변경할 월 임금을 입력해 주세요.")),
                List.of(),
                List.of(),
                validVersions(),
                1,
                180
        );
    }

    public static AiAnalysisResponse responseWithCandidate(AiCandidate candidate) {
        return new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.REVIEW_REQUIRED,
                null,
                List.of(),
                List.of(candidate),
                List.of(),
                validVersions(),
                1,
                245
        );
    }

    public static AiCandidate validCandidate() {
        return new AiCandidate(
                "candidate-1",
                WORKER_REF,
                WORKFLOW_ID,
                Map.of("stay_expiry_date", "2026-12-31"),
                List.of("contract_end_date", "monthly_wage"),
                new BigDecimal("0.92")
        );
    }

    public static AiRuntimeVersions validVersions() {
        return new AiRuntimeVersions(
                "agent-1.0.0",
                "openai",
                "gpt-5-mini",
                "2026-07-01",
                "prompt-3",
                "context-0.2.0",
                KNOWLEDGE_VERSION,
                CONTRACT_VERSION
        );
    }
}
