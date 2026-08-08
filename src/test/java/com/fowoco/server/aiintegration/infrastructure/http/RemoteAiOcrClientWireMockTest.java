package com.fowoco.server.aiintegration.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.ocr.AiOcrDocumentType;
import com.fowoco.server.aiintegration.application.ocr.AiOcrFile;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RemoteAiOcrClientWireMockTest {

    private static final UUID REQUEST_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOCUMENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String PATH = "/internal/v1/ocr/worker-documents/" + DOCUMENT_ID;
    private static final String TRACEPARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private final ObjectMapper objectMapper = AiOcrHttpConfiguration.createContractObjectMapper(
            new ObjectMapper()
    );

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
    void sendsAuthenticatedMultipartRequestAndParsesSnakeCaseResponse() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "request_id":"10000000-0000-0000-0000-000000000001",
                          "worker_document_id":"20000000-0000-0000-0000-000000000001",
                          "ocr_status":"SUCCEEDED",
                          "matched_template_id":1,
                          "document_side":"FRONT",
                          "fields":{"passport_number":"M12345678"},
                          "field_confidences":{"passport_number":0.99},
                          "review_reasons":[]
                        }
                        """)));
        RemoteAiOcrClient client = client();

        AiOcrResponse response = client.recognize(request(), new AiRuntimeCallContext(TRACEPARENT));

        assertThat(response.fields()).containsEntry("passport_number", "M12345678");
        wireMock.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer test-service-credential"))
                .withHeader("X-Request-Id", equalTo(REQUEST_ID.toString()))
                .withHeader("traceparent", equalTo(TRACEPARENT))
                .withHeader("Content-Type", matching("multipart/form-data; boundary=.*"))
                .withRequestBody(containing("name=\"request_id\""))
                .withRequestBody(containing(REQUEST_ID.toString()))
                .withRequestBody(containing("name=\"document_type\""))
                .withRequestBody(containing("PASSPORT_COPY"))
                .withRequestBody(containing("name=\"country_code\""))
                .withRequestBody(containing("VNM"))
                .withRequestBody(containing("filename=\"passport.jpg\"")));
    }

    @Test
    void classifiesServerFailureWithoutExposingResponseBody() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(503)
                .withBody("secret-provider-error")));

        assertThatThrownBy(() -> client().recognize(request(), AiRuntimeCallContext.withoutTrace()))
                .isInstanceOfSatisfying(AiRuntimeCallException.class, exception -> {
                    assertThat(exception.failureCode()).isEqualTo(AiRuntimeFailureCode.RUNTIME_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain("secret-provider-error");
                });
    }

    @Test
    void rejectsNumericOcrFieldInsteadOfCoercingItToSensitiveText() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "request_id":"10000000-0000-0000-0000-000000000001",
                          "worker_document_id":"20000000-0000-0000-0000-000000000001",
                          "ocr_status":"SUCCEEDED",
                          "matched_template_id":1,
                          "document_side":"FRONT",
                          "fields":{"passport_number":12345},
                          "field_confidences":{"passport_number":0.99},
                          "review_reasons":[]
                        }
                        """)));

        assertThatThrownBy(() -> client().recognize(request(), AiRuntimeCallContext.withoutTrace()))
                .isInstanceOfSatisfying(AiRuntimeCallException.class, exception ->
                        assertThat(exception.failureCode())
                                .isEqualTo(AiRuntimeFailureCode.RESPONSE_PARSING_FAILED));
    }

    private RemoteAiOcrClient client() {
        return new RemoteAiOcrClient(
                URI.create(wireMock.baseUrl() + "/internal/v1/ocr/worker-documents"),
                "Bearer test-service-credential",
                Duration.ofSeconds(2),
                1_048_576,
                2,
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
                objectMapper,
                new AiRuntimeCircuitBreaker(5, Duration.ofSeconds(30), Clock.systemUTC())
        );
    }

    private AiOcrRequest request() {
        return new AiOcrRequest(
                REQUEST_ID,
                DOCUMENT_ID,
                AiOcrDocumentType.PASSPORT_COPY,
                "VNM",
                new AiOcrFile(
                        "passport.jpg",
                        "image/jpeg",
                        "image-content".getBytes(StandardCharsets.UTF_8)
                )
        );
    }
}
