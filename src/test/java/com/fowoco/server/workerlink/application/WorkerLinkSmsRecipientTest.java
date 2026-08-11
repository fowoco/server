package com.fowoco.server.workerlink.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkerLinkSmsRecipientTest {

    @Test
    void normalizesKoreanMobileNumberWithoutPersistingFormatting() {
        assertThat(WorkerLinkSmsRecipient.normalizeKoreanMobile("010-1234-5678"))
                .isEqualTo("01012345678");
        assertThat(WorkerLinkSmsRecipient.normalizeKoreanMobile("+82 10 1234 5678"))
                .isEqualTo("01012345678");
    }

    @Test
    void rejectsNonMobileOrOverseasNumberForCurrentMvp() {
        assertThatThrownBy(() -> WorkerLinkSmsRecipient.normalizeKoreanMobile("02-1234-5678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkerLinkSmsRecipient.normalizeKoreanMobile("+84 90 123 4567"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
