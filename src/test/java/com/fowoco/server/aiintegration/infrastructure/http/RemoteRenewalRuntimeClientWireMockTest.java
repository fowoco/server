package com.fowoco.server.aiintegration.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.renewal.RenewalCompanySnapshot;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import com.fowoco.server.aiintegration.application.renewal.RenewalTaskSnapshot;
import com.fowoco.server.aiintegration.application.renewal.RenewalWorkerSnapshot;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;

class RemoteRenewalRuntimeClientWireMockTest {

    private static final String PATH = "/internal/v1/workflows/renewal/run";
    private final ObjectMapper objectMapper = new ObjectMapper().rebuild()
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private WireMockServer wireMock;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    @Test
    void sendsTheAgentOwnedContractWithBearerAuthentication() {
        RenewalRunRequest request = request();
        wireMock.stubFor(post(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer renewal-test-token"))
                .withHeader("X-Request-Id", equalTo(request.requestId().toString()))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.worker.stayExpiryDate", equalTo("2027-08-31")
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.task.workflowId", equalTo("WF-CON-001")
                ))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson(request))));

        RenewalRunResponse response = client().run(request, AiRuntimeCallContext.withoutTrace());

        assertThat(response.intent()).isEqualTo("EXPIRY_RENEWAL");
        assertThat(response.workflowId()).isEqualTo("WF-CON-001");
        assertThat(response.requestedFields()).extracting("key").containsExactly("wage");
    }

    @Test
    void decodesGeneratedDocumentValuesAndIgnoresTheAgentLocalPathForLaterUse() {
        RenewalRunRequest request = request();
        wireMock.stubFor(post(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(generatedResponseJson(request))));

        RenewalRunResponse response = client().run(request, AiRuntimeCallContext.withoutTrace());

        assertThat(response.generatedDocuments()).hasSize(1);
        assertThat(response.generatedDocuments().get(0).templateId())
                .isEqualTo("standard_labor_contract_v6");
        assertThat(response.generatedDocuments().get(0).path()).isEqualTo("/tmp/agent-only.hwp");
        assertThat(response.generatedDocuments().get(0).values())
                .containsEntry("employee_name", "NGUYEN VAN AN");
    }

    @Test
    void decodesTheWorkerGuideReviewContract() {
        RenewalRunRequest request = request();
        wireMock.stubFor(post(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(workerGuideReviewResponseJson(request))));

        RenewalRunResponse response = client().run(request, AiRuntimeCallContext.withoutTrace());

        assertThat(response.guideReviewRequired()).isTrue();
        assertThat(response.guideFailureCode())
                .isEqualTo("LANGUAGE_ASSISTANT_NOT_CONFIGURED");
        assertThat(response.workerRequestMessage()).isNull();
        assertThat(response.caseSignals()).containsExactly("REVIEW_WORKER_GUIDE");
    }

    private RemoteRenewalRuntimeClient client() {
        return new RemoteRenewalRuntimeClient(
                URI.create(wireMock.baseUrl() + PATH),
                "Bearer renewal-test-token",
                Duration.ofSeconds(5),
                1_048_576,
                2,
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
                objectMapper,
                new AiRuntimeCircuitBreaker(5, Duration.ofSeconds(30), Clock.systemUTC())
        );
    }

    private RenewalRunRequest request() {
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        return new RenewalRunRequest(
                requestId, attemptId, "응웬반안 체류연장 준비해줘", workerId, companyId, taskId,
                Map.of("stay_expiry_date", "2027-08-31"), List.of(), null,
                new RenewalWorkerSnapshot(
                        workerId, companyId, "응웬반안", "VN", "vi", "ACTIVE", "E-9",
                        java.time.LocalDate.parse("2027-08-31"), null, null, null, null, now, now, 0
                ),
                new RenewalCompanySnapshot(companyId, "테스트 사업장", "ACTIVE", now, now, 0),
                new RenewalTaskSnapshot(
                        taskId, companyId, workerId, caseId, "RECONTRACT", "WF-CON-001", "0.2.0",
                        "재계약", null, Map.of(), 0, "AI_CANDIDATE", "DRAFT", null,
                        actorId, actorId, now, now, 0
                )
        );
    }

    private String responseJson(RenewalRunRequest request) {
        return """
                {
                  "requestId":"%s",
                  "attemptId":"%s",
                  "taskId":"%s",
                  "intent":"EXPIRY_RENEWAL",
                  "workflowId":"WF-CON-001",
                  "confidence":0.91,
                  "status":"NEEDS_INFO",
                  "outcome":"NEEDS_INFO",
                  "scenario":"ask_hr",
                  "phase":"PHASE_2",
                  "step":"STEP_5",
                  "slots":{},
                  "missingSlots":["wage"],
                  "requestedFields":[{"key":"wage","sourceHint":"USER_INPUT"}],
                  "guideMessage":"임금을 확인해 주세요.",
                  "workerRequestMessage":null,
                  "languageAssistant":null,
                  "ocrResult":null,
                  "generatedDocuments":[],
                  "evidence":[],
                  "documentValidation":null,
                  "caseSignals":["REQUEST_CONTRACT_SLOTS","NEEDS_INFO"],
                  "progressEvents":[],
                  "supervisorReason":null,
                  "supervisorSource":"rules",
                  "activeSubgraph":"main",
                  "errors":[]
                }
                """.formatted(request.requestId(), request.attemptId(), request.taskId());
    }

    private String generatedResponseJson(RenewalRunRequest request) {
        return """
                {
                  "requestId":"%s",
                  "attemptId":"%s",
                  "taskId":"%s",
                  "intent":"EXPIRY_RENEWAL",
                  "workflowId":"WF-CON-001",
                  "confidence":0.94,
                  "status":"READY_FOR_REVIEW",
                  "outcome":"REVIEW_REQUIRED",
                  "scenario":"generate",
                  "phase":"PHASE_4",
                  "step":"STEP_13",
                  "slots":{},
                  "missingSlots":[],
                  "requestedFields":[],
                  "guideMessage":null,
                  "workerRequestMessage":null,
                  "languageAssistant":null,
                  "ocrResult":null,
                  "generatedDocuments":[{
                    "template_id":"standard_labor_contract_v6",
                    "name":"표준근로계약서",
                    "format":"hwp",
                    "status":"generated",
                    "path":"/tmp/agent-only.hwp",
                    "mapped_fields":["employee_name"],
                    "changed_fields":["employee_name"],
                    "values":{"employee_name":"NGUYEN VAN AN"}
                  }],
                  "evidence":[],
                  "documentValidation":null,
                  "caseSignals":["GENERATE_DRAFTS","READY_FOR_REVIEW"],
                  "progressEvents":[],
                  "supervisorReason":null,
                  "supervisorSource":"rules",
                  "activeSubgraph":"main",
                  "errors":[]
                }
                """.formatted(request.requestId(), request.attemptId(), request.taskId());
    }

    private String workerGuideReviewResponseJson(RenewalRunRequest request) {
        return """
                {
                  "requestId":"%s",
                  "attemptId":"%s",
                  "taskId":"%s",
                  "intent":"EXPIRY_RENEWAL",
                  "workflowId":"WF-CON-001",
                  "confidence":0.91,
                  "status":"READY_FOR_REVIEW",
                  "outcome":"REVIEW_REQUIRED",
                  "scenario":"ask_worker",
                  "phase":"PHASE_3",
                  "step":"STEP_5",
                  "slots":{},
                  "missingSlots":["passport_number"],
                  "requestedFields":[{"key":"passport_number","sourceHint":"DOCUMENT_OCR"}],
                  "guideMessage":null,
                  "workerRequestMessage":null,
                  "guideReviewRequired":true,
                  "guideFailureCode":"LANGUAGE_ASSISTANT_NOT_CONFIGURED",
                  "languageAssistant":null,
                  "ocrResult":null,
                  "generatedDocuments":[],
                  "evidence":[],
                  "documentValidation":null,
                  "caseSignals":["REVIEW_WORKER_GUIDE"],
                  "progressEvents":[],
                  "supervisorReason":null,
                  "supervisorSource":"rules",
                  "activeSubgraph":"main",
                  "errors":[]
                }
                """.formatted(request.requestId(), request.attemptId(), request.taskId());
    }
}
