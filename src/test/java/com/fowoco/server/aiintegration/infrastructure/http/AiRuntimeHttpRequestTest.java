package com.fowoco.server.aiintegration.infrastructure.http;

import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validPlanRequest;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validRequest;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AiRuntimeHttpRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void planJsonContainsOnlyRequestIdPhaseAndInstruction() {
        JsonNode json = objectMapper.valueToTree(AiRuntimeHttpRequest.from(validPlanRequest()));
        JsonNode input = json.get("analysisInput");

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder("requestId", "phase", "analysisInput");
        assertThat(json.get("phase").textValue()).isEqualTo("PLAN");
        assertThat(fieldNames(input)).containsExactly("instruction");
        assertThat(input.get("instruction").textValue())
                .isEqualTo("응웬반안 체류연장 준비해줘");
    }

    @Test
    void analyzeJsonContainsOnlyRequestedWorkerFields() {
        JsonNode json = objectMapper.valueToTree(AiRuntimeHttpRequest.from(validRequest()));
        JsonNode input = json.get("analysisInput");
        JsonNode worker = input.get("workers").get(0);

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder("requestId", "phase", "analysisInput");
        assertThat(fieldNames(input))
                .containsExactlyInAnyOrder(
                        "instruction",
                        "plannedIntent",
                        "plannedWorkflowId",
                        "agentTarget",
                        "requestedFieldKeys",
                        "workers"
                );
        assertThat(input.get("plannedIntent").textValue()).isEqualTo("EXPIRY_RENEWAL");
        assertThat(input.get("plannedWorkflowId").textValue()).isEqualTo("WF-STY-001");
        assertThat(input.get("agentTarget").textValue()).isEqualTo("renewal-agent");
        assertThat(fieldNames(worker))
                .containsExactlyInAnyOrder("workerRef", "requestedFields");
        assertThat(worker.get("requestedFields").get("legal_name").textValue())
                .isEqualTo("NGUYEN VAN AN");
        assertThat(json.toString().toLowerCase())
                .doesNotContain(
                        "attemptid",
                        "contractversion",
                        "requiredknowledgeversion",
                        "deadlinems",
                        "token",
                        "authorization",
                        "password",
                        "api_key"
                );
    }

    private java.util.List<String> fieldNames(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).toList();
    }
}
