package com.fowoco.server.aiintegration.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.aiintegration.application.document.DocumentGenerationRequest;
import com.fowoco.server.aiintegration.application.document.GeneratedDocumentFile;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RemoteDocumentGenerationClientWireMockTest {

    private static final String PATH = "/api/v1/documents/generate";
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
    void sendsTemplateAndValuesAsMultipartPayloadAndReturnsFileBytes() {
        byte[] generated = "generated-hwp".getBytes(StandardCharsets.UTF_8);
        wireMock.stubFor(post(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer document-test-token"))
                .withHeader("Content-Type", containing("multipart/form-data; boundary="))
                .withRequestBody(containing("name=\"payload\""))
                .withRequestBody(containing("\"template_id\":\"standard_labor_contract_v6\""))
                .withRequestBody(containing("\"employee_name\":\"NGUYEN VAN AN\""))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Disposition", "attachment; filename=contract.hwp")
                        .withHeader("X-Document-Template-Id", "standard_labor_contract_v6")
                        .withBody(generated)));

        GeneratedDocumentFile result = client().generate(new DocumentGenerationRequest(
                "standard_labor_contract_v6",
                "hwp",
                Map.of("employee_name", "NGUYEN VAN AN")
        ));

        assertThat(result.fileName()).isEqualTo("contract.hwp");
        assertThat(result.format()).isEqualTo("hwp");
        assertThat(result.content()).isEqualTo(generated);
    }

    private RemoteDocumentGenerationClient client() {
        return new RemoteDocumentGenerationClient(
                URI.create(wireMock.baseUrl() + PATH),
                "Bearer document-test-token",
                Duration.ofSeconds(5),
                20 * 1_024 * 1_024,
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
                new ObjectMapper()
        );
    }
}
