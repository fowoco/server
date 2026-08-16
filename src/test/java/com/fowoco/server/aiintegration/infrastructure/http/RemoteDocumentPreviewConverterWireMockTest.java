package com.fowoco.server.aiintegration.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.file.application.DocumentPreviewConversionException;
import com.fowoco.server.file.application.DocumentPreviewSource;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemoteDocumentPreviewConverterWireMockTest {

    private static final String PATH = "/api/v1/documents/convert";
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
    void sendsSourceAndPdfTargetAsMultipartAndReturnsPdf() {
        byte[] pdf = "%PDF-1.7 converted".getBytes(StandardCharsets.US_ASCII);
        wireMock.stubFor(post(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer preview-test-token"))
                .withHeader("Accept", equalTo("application/pdf"))
                .withHeader("Content-Type", containing("multipart/form-data; boundary="))
                .withRequestBody(containing("name=\"file\"; filename=\"contract.hwp\""))
                .withRequestBody(containing("hwp-source"))
                .withRequestBody(containing("name=\"target_format\""))
                .withRequestBody(containing("pdf"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdf)));

        byte[] result = client().convertToPdf(new DocumentPreviewSource(
                "contract.hwp",
                "application/octet-stream",
                "hwp-source".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(result).isEqualTo(pdf);
    }

    @Test
    void rejectsSuccessfulResponseWithoutPdfSignature() {
        wireMock.stubFor(post(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody("not-a-pdf")));

        assertThatThrownBy(() -> client().convertToPdf(new DocumentPreviewSource(
                "contract.hwpx",
                "application/hwp+zip",
                "hwpx-source".getBytes(StandardCharsets.UTF_8)
        )))
                .isInstanceOfSatisfying(DocumentPreviewConversionException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(DocumentPreviewConversionException.Reason.INVALID_DOCUMENT)
                );
    }

    @Test
    void mapsUnprocessableResponseToInvalidDocument() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(422)));

        assertThatThrownBy(() -> client().convertToPdf(new DocumentPreviewSource(
                "broken.hwp",
                "application/octet-stream",
                "broken".getBytes(StandardCharsets.UTF_8)
        )))
                .isInstanceOfSatisfying(DocumentPreviewConversionException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(DocumentPreviewConversionException.Reason.INVALID_DOCUMENT)
                );
    }

    private RemoteDocumentPreviewConverter client() {
        return new RemoteDocumentPreviewConverter(
                URI.create(wireMock.baseUrl() + PATH),
                "Bearer preview-test-token",
                Duration.ofSeconds(5),
                20 * 1_024 * 1_024,
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
        );
    }
}
