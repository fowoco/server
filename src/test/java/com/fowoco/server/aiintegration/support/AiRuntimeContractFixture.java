package com.fowoco.server.aiintegration.support;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiCandidate;
import com.fowoco.server.aiintegration.application.model.AiRuntimeVersions;
import com.fowoco.server.aiintegration.application.model.MaskedAnalysisInput;
import com.fowoco.server.aiintegration.application.model.MaskedWorkerContext;
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
    public static final String WORKFLOW_ID = "EXPIRY_RENEWAL";

    private AiRuntimeContractFixture() {
    }

    public static AiAnalysisRequest validRequest() {
        return requestWithInstruction(
                "workerRef 30000000-0000-0000-0000-000000000001의 체류연장 준비"
        );
    }

    public static AiAnalysisRequest requestWithInstruction(String instruction) {
        return new AiAnalysisRequest(
                REQUEST_ID,
                ATTEMPT_ID,
                CONTRACT_VERSION,
                KNOWLEDGE_VERSION,
                10_000,
                new MaskedAnalysisInput(
                        instruction,
                        List.of(new MaskedWorkerContext(
                                WORKER_REF,
                                "vi",
                                "ACTIVE",
                                LocalDate.of(2026, 12, 31)
                        )),
                        List.of(new WorkflowConstraint(
                                WORKFLOW_ID,
                                Set.of("stay_expiry_date", "contract_end_date", "monthly_wage")
                        ))
                )
        );
    }

    public static AiAnalysisResponse validResponse() {
        return responseWithCandidate(validCandidate());
    }

    public static AiAnalysisResponse responseWithCandidate(AiCandidate candidate) {
        return new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.REVIEW_REQUIRED,
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
