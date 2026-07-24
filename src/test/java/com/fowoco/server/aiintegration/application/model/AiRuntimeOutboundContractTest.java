package com.fowoco.server.aiintegration.application.model;

import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validRequest;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AiRuntimeOutboundContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void outboundJsonContainsOnlyTheExplicitMaskedContract() throws Exception {
        JsonNode json = objectMapper.valueToTree(validRequest());
        JsonNode worker = json.get("maskedInput").get("workers").get(0);

        assertThat(json.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder(
                        "requestId",
                        "attemptId",
                        "contractVersion",
                        "requiredKnowledgeVersion",
                        "deadlineMs",
                        "maskedInput"
                );
        assertThat(worker.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder(
                        "workerRef",
                        "preferredLanguage",
                        "workStatus",
                        "stayExpiryDate"
                );
        assertThat(json.toString().toLowerCase())
                .doesNotContain(
                        "passportnumber",
                        "alienregistrationnumber",
                        "phone",
                        "accountnumber",
                        "legalname",
                        "token",
                        "authorization"
                );
    }
}
