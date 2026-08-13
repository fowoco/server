package com.fowoco.server.airun.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class AiRunPublicErrorCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "RUNTIME_DISABLED",
            "DEADLINE_EXCEEDED",
            "AUTHENTICATION_FAILED",
            "RUNTIME_UNAVAILABLE",
            "TRANSPORT_FAILURE"
    })
    void hidesInternalAvailabilityFailureBehindStableClientCode(String internalCode) {
        assertThat(AiRunPublicErrorCode.fromInternal(internalCode)).isEqualTo("AI_UNAVAILABLE");
    }

    @Test
    void preservesBusinessOrContractFailureForReview() {
        assertThat(AiRunPublicErrorCode.fromInternal("UNEXPECTED_WORKFLOW"))
                .isEqualTo("UNEXPECTED_WORKFLOW");
        assertThat(AiRunPublicErrorCode.fromInternal(null)).isNull();
    }
}
