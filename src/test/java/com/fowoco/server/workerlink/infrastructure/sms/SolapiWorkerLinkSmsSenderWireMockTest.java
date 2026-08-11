package com.fowoco.server.workerlink.infrastructure.sms;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.workerlink.application.port.WorkerLinkSmsMessage;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsProviderException;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SolapiWorkerLinkSmsSenderWireMockTest {

    private static final String PATH = "/messages/v4/send-many/detail";
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T01:02:03Z"),
            ZoneOffset.UTC
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
    void sendsOnlyRequiredMessageFieldsWithHmacAuthorization() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "failedMessageList":[],
                          "groupInfo":{"groupId":"group-1"},
                          "messageList":[]
                        }
                        """)));

        sender().send(new WorkerLinkSmsMessage(
                "01012345678",
                "[FOWOCO] 요청입니다. https://demo.fowoco.test/worker-portal/token"
        ));

        wireMock.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo(
                        "HMAC-SHA256 Apikey=test-key, Date=2026-08-11T01:02:03Z, "
                                + "salt=fixedsalt, "
                                + "signature=b4fa4d00b1ded7750d90b0f1cbe65e894612cb6a87f5753cd9aaaaab45eb4f7b"
                ))
                .withRequestBody(matchingJsonPath("$.messages[0].to", equalTo("01012345678")))
                .withRequestBody(matchingJsonPath("$.messages[0].from", equalTo("029999999")))
                .withRequestBody(matchingJsonPath(
                        "$.messages[0].text",
                        equalTo("[FOWOCO] 요청입니다. https://demo.fowoco.test/worker-portal/token")
                )));
    }

    @Test
    void mapsProviderErrorToSafeApplicationException() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(503)
                .withBody("provider-secret-error")));

        assertThatThrownBy(() -> sender().send(new WorkerLinkSmsMessage(
                "01012345678",
                "[FOWOCO] 테스트"
        ))).isInstanceOfSatisfying(WorkerLinkSmsProviderException.class, exception -> {
            org.assertj.core.api.Assertions.assertThat(exception.failureType())
                    .isEqualTo(WorkerLinkSmsProviderException.FailureType.REJECTED);
            org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                    .doesNotContain("provider-secret-error");
        });
    }

    @Test
    void rejectsSuccessfulHttpResponseThatContainsFailedMessage() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "failedMessageList":[{"errorCode":"ValidationError"}],
                          "groupInfo":{"groupId":"group-1"}
                        }
                        """)));

        assertThatThrownBy(() -> sender().send(new WorkerLinkSmsMessage(
                "01012345678",
                "[FOWOCO] 테스트"
        ))).isInstanceOfSatisfying(WorkerLinkSmsProviderException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.failureType())
                        .isEqualTo(WorkerLinkSmsProviderException.FailureType.REJECTED));
    }

    @Test
    void mapsProviderTimeoutToSafeApplicationException() {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(500)
                .withBody("""
                        {"failedMessageList":[],"groupInfo":{"groupId":"group-1"}}
                        """)));

        assertThatThrownBy(() -> sender(Duration.ofMillis(100)).send(new WorkerLinkSmsMessage(
                "01012345678",
                "[FOWOCO] 테스트"
        ))).isInstanceOfSatisfying(WorkerLinkSmsProviderException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.failureType())
                        .isEqualTo(WorkerLinkSmsProviderException.FailureType.UNKNOWN));
    }

    @Test
    void rejectsMissingSolapiCredentialAtStartup() {
        WorkerLinkDeliveryProperties.Sms invalid = new WorkerLinkDeliveryProperties.Sms(
                "solapi",
                endpoint(),
                "",
                "test-secret",
                "029999999",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                65_536
        );

        assertThatThrownBy(() -> new SolapiWorkerLinkSmsSender(
                invalid,
                new ObjectMapper(),
                HttpClient.newHttpClient(),
                FIXED_CLOCK,
                () -> "fixedsalt"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("apiKey");
    }

    private SolapiWorkerLinkSmsSender sender() {
        return sender(Duration.ofSeconds(2));
    }

    private SolapiWorkerLinkSmsSender sender(Duration overallTimeout) {
        return new SolapiWorkerLinkSmsSender(
                properties(overallTimeout),
                new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                FIXED_CLOCK,
                () -> "fixedsalt"
        );
    }

    private WorkerLinkDeliveryProperties.Sms properties() {
        return properties(Duration.ofSeconds(2));
    }

    private WorkerLinkDeliveryProperties.Sms properties(Duration overallTimeout) {
        return new WorkerLinkDeliveryProperties.Sms(
                "solapi",
                endpoint(),
                "test-key",
                "test-secret",
                "029999999",
                Duration.ofSeconds(1),
                overallTimeout,
                65_536
        );
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + wireMock.port() + PATH);
    }
}
