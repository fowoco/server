package com.fowoco.server.aiintegration.infrastructure.http;

import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.REQUEST_ID;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.contextRequiredResponse;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validPlanRequest;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validRequest;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;

class RemoteAiRuntimeClientWireMockTest {

    private static final String PATH = "/internal/v1/analyses";
    private static final String TRACEPARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private final ObjectMapper objectMapper = new ObjectMapper().rebuild()
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void sendsCanonicalHeadersAndCamelCaseBodyThenParsesResponse() throws Exception {
        wireMock.stubFor(post(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer test-service-credential"))
                .withHeader("X-Request-Id", equalTo(REQUEST_ID.toString()))
                .withHeader("traceparent", equalTo(TRACEPARENT))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.requestId",
                        equalTo(REQUEST_ID.toString())
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.phase",
                        equalTo("ANALYZE")
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.analysisInput.workers[0].stayExpiryDate"
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.analysisInput.workers[0].requestedFields.legal_name",
                        equalTo("NGUYEN VAN AN")
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.request_id",
                        absent()
                ))
                .willReturn(jsonResponse(objectMapper.writeValueAsString(validResponse()))));

        AiAnalysisResponse response = client(1_048_576, 8, 5, Duration.ofSeconds(30))
                .analyze(validRequest(), new AiRuntimeCallContext(TRACEPARENT));

        assertThat(response).isEqualTo(validResponse());
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void sendsInstructionOnlyPlanAndParsesContextRequirement() throws Exception {
        wireMock.stubFor(post(urlEqualTo(PATH))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.phase",
                        equalTo("PLAN")
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.analysisInput.instruction",
                        equalTo("응웬반안 체류연장 준비해줘, EXPIRY_RENEWAL")
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.analysisInput.intentHint",
                        absent()
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.analysisInput.extractedSlots",
                        absent()
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.analysisInput.requestedFieldKeys",
                        absent()
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.analysisInput.workers",
                        absent()
                ))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.analysisInput.workflowConstraints",
                        absent()
                ))
                .willReturn(jsonResponse(objectMapper.writeValueAsString(contextRequiredResponse()))));

        AiAnalysisResponse response = client(1_048_576, 8, 5, Duration.ofSeconds(30))
                .analyze(validPlanRequest(), new AiRuntimeCallContext(TRACEPARENT));

        assertThat(response).isEqualTo(contextRequiredResponse());
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void doesNotRetryAndOpensCircuitAfterConsecutiveRuntimeFailures() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));
        AiRuntimeClient client = client(1_048_576, 8, 2, Duration.ofSeconds(30));

        assertFailureCode(client, AiRuntimeFailureCode.RUNTIME_UNAVAILABLE);
        assertFailureCode(client, AiRuntimeFailureCode.RUNTIME_UNAVAILABLE);
        assertFailureCode(client, AiRuntimeFailureCode.CIRCUIT_OPEN);

        wireMock.verify(exactly(2), postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void rejectsUnknownResponseFieldWithStableParsingFailure() throws Exception {
        String response = objectMapper.writeValueAsString(validResponse());
        String responseWithUnknownField = response.substring(0, response.length() - 1)
                + ",\"unexpected\":\"value\"}";
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(jsonResponse(responseWithUnknownField)));

        assertFailureCode(
                client(1_048_576, 8, 5, Duration.ofSeconds(30)),
                AiRuntimeFailureCode.RESPONSE_PARSING_FAILED
        );
    }

    @Test
    void enforcesOverallDeadline() {
        wireMock.stubFor(post(urlEqualTo(PATH))
                .willReturn(jsonResponse("{}").withFixedDelay(2_000)));

        AiRuntimeClient client = client(
                1_048_576,
                8,
                5,
                Duration.ofSeconds(1)
        );

        assertFailureCode(client, AiRuntimeFailureCode.DEADLINE_EXCEEDED);
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void mapsRuntimeRequestTimeoutToDeadlineExceeded() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(408)));

        assertFailureCode(
                client(1_048_576, 8, 5, Duration.ofSeconds(30)),
                AiRuntimeFailureCode.DEADLINE_EXCEEDED
        );
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void cancelsOversizedResponseBody() {
        wireMock.stubFor(post(urlEqualTo(PATH))
                .willReturn(jsonResponse("\"" + "x".repeat(2_000) + "\"")));

        assertFailureCode(
                client(1_024, 8, 5, Duration.ofSeconds(30)),
                AiRuntimeFailureCode.RESPONSE_TOO_LARGE
        );
    }

    @Test
    void rejectsConcurrentCallWhenBulkheadIsFull() throws Exception {
        wireMock.stubFor(post(urlEqualTo(PATH))
                .willReturn(jsonResponse(objectMapper.writeValueAsString(validResponse()))
                        .withFixedDelay(500)));
        AiRuntimeClient client = client(1_048_576, 1, 5, Duration.ofSeconds(30));

        CompletableFuture<AiAnalysisResponse> first = CompletableFuture.supplyAsync(
                () -> client.analyze(validRequest())
        );
        awaitReceivedRequest();

        assertFailureCode(client, AiRuntimeFailureCode.BULKHEAD_FULL);
        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(validResponse());
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(PATH)));
    }

    private RemoteAiRuntimeClient client(
            int maxResponseBytes,
            int maxConcurrentCalls,
            int failureThreshold,
            Duration overallTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new RemoteAiRuntimeClient(
                URI.create(wireMock.baseUrl() + PATH),
                "Bearer test-service-credential",
                overallTimeout,
                maxResponseBytes,
                maxConcurrentCalls,
                httpClient,
                objectMapper,
                new AiRuntimeCircuitBreaker(
                        failureThreshold,
                        Duration.ofSeconds(30),
                        Clock.systemUTC()
                )
        );
    }

    private void assertFailureCode(AiRuntimeClient client, AiRuntimeFailureCode expected) {
        assertThatThrownBy(() -> client.analyze(validRequest()))
                .isInstanceOfSatisfying(
                        AiRuntimeCallException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo(expected)
                );
    }

    private void awaitReceivedRequest() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (wireMock.getAllServeEvents().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(wireMock.getAllServeEvents()).hasSize(1);
    }

    private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonResponse(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
