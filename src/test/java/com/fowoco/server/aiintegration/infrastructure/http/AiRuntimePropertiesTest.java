package com.fowoco.server.aiintegration.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiRuntimePropertiesTest {

    @Test
    void usesFiveMinuteDeadlineByDefault() {
        AiRuntimeProperties properties = new AiRuntimeProperties();

        assertThat(properties.getOverallTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.attemptDeadlineMs()).isEqualTo(300_000L);
        assertThat(properties.getDocumentConversionTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void usesConfiguredOverallTimeoutAsAttemptDeadline() {
        AiRuntimeProperties properties = new AiRuntimeProperties();

        properties.setOverallTimeout(Duration.ofSeconds(300));

        assertThat(properties.getOverallTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(properties.attemptDeadlineMs()).isEqualTo(300_000L);
    }

    @Test
    void rejectsOverallTimeoutLongerThanContractMaximum() {
        AiRuntimeProperties properties = new AiRuntimeProperties();

        assertThatThrownBy(() -> properties.setOverallTimeout(Duration.ofMinutes(5).plusMillis(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("overallTimeout must not exceed 5m");
    }

    @Test
    void rejectsAnalysisEndpointConfiguredAsRenewalEndpoint() {
        AiRuntimeProperties properties = new AiRuntimeProperties();
        properties.setServiceCredential("test-token");
        properties.setRenewalEndpoint(URI.create("http://ai:8000/internal/v1/analyses"));

        assertThatThrownBy(properties::validateEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "AI_RUNTIME_RENEWAL_ENDPOINT must end with /internal/v1/workflows/renewal/run"
                );
    }

    @Test
    void acceptsDeploymentEndpointsWithAProxyPrefix() {
        AiRuntimeProperties properties = new AiRuntimeProperties();
        properties.setServiceCredential("test-token");
        properties.setEndpoint(URI.create("https://agent.example.com/fowoco/internal/v1/analyses"));
        properties.setRenewalEndpoint(URI.create(
                "https://agent.example.com/fowoco/internal/v1/workflows/renewal/run/"
        ));

        properties.validateEnabledConfiguration();
    }
}
