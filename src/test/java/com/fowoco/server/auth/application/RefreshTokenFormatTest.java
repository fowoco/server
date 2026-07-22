package com.fowoco.server.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenFormatTest {

    @Test
    void acceptsOnlyTheUrlSafeUnpaddedValueProducedByTheGenerator() {
        assertThat(RefreshTokenFormat.isValidRawValue("A".repeat(43))).isTrue();
        assertThat(RefreshTokenFormat.isValidRawValue("a-b_c" + "0".repeat(38))).isTrue();

        assertThat(RefreshTokenFormat.isValidRawValue(null)).isFalse();
        assertThat(RefreshTokenFormat.isValidRawValue("A".repeat(42))).isFalse();
        assertThat(RefreshTokenFormat.isValidRawValue("A".repeat(44))).isFalse();
        assertThat(RefreshTokenFormat.isValidRawValue("+" + "A".repeat(42))).isFalse();
        assertThat(RefreshTokenFormat.isValidRawValue("A".repeat(42) + "=")).isFalse();
    }
}
