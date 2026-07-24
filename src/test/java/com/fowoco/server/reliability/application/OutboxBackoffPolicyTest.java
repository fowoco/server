package com.fowoco.server.reliability.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.reliability.config.OutboxProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxBackoffPolicyTest {

    @Test
    void doublesDelayPerAttemptAndCapsAtMaximum() {
        OutboxProperties properties = new OutboxProperties();
        properties.setInitialBackoff(Duration.ofSeconds(2));
        properties.setMaxBackoff(Duration.ofSeconds(10));
        OutboxBackoffPolicy policy = new OutboxBackoffPolicy(properties);

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayForAttempt(100)).isEqualTo(Duration.ofSeconds(10));
    }
}
