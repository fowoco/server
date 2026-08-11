package com.fowoco.server.aiintegration.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiRuntimePropertiesTest {

    @Test
    void usesFourMinuteDeadlineByDefault() {
        AiRuntimeProperties properties = new AiRuntimeProperties();

        assertThat(properties.getOverallTimeout()).isEqualTo(Duration.ofMinutes(4));
        assertThat(properties.attemptDeadlineMs()).isEqualTo(240_000L);
    }

    @Test
    void usesConfiguredOverallTimeoutAsAttemptDeadline() {
        AiRuntimeProperties properties = new AiRuntimeProperties();

        properties.setOverallTimeout(Duration.ofSeconds(180));

        assertThat(properties.getOverallTimeout()).isEqualTo(Duration.ofSeconds(180));
        assertThat(properties.attemptDeadlineMs()).isEqualTo(180_000L);
    }

    @Test
    void rejectsOverallTimeoutLongerThanContractMaximum() {
        AiRuntimeProperties properties = new AiRuntimeProperties();

        assertThatThrownBy(() -> properties.setOverallTimeout(Duration.ofMinutes(5).plusMillis(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("overallTimeout must not exceed 5m");
    }
}
