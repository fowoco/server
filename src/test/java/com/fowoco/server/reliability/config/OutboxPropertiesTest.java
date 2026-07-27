package com.fowoco.server.reliability.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxPropertiesTest {

    @Test
    void leaseDurationCannotExceedDatabaseFunctionBoundary() {
        OutboxProperties properties = new OutboxProperties();

        properties.setLeaseDuration(Duration.ofDays(1));

        assertThat(properties.getLeaseDuration()).isEqualTo(Duration.ofDays(1));
        assertThatThrownBy(
                () -> properties.setLeaseDuration(Duration.ofDays(1).plusMillis(1))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 day");
    }
}
