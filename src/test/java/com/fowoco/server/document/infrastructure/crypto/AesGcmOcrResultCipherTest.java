package com.fowoco.server.document.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AesGcmOcrResultCipherTest {

    @Test
    void encryptsWithTenantAndRunBoundAuthenticatedData() {
        AesGcmOcrResultCipher cipher = new AesGcmOcrResultCipher(
                new byte[32],
                "test-v1",
                new SecureRandom()
        );
        UUID companyId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        byte[] plaintext = "M12345678".getBytes(StandardCharsets.UTF_8);

        String encrypted = cipher.encrypt(plaintext, companyId, runId);

        assertThat(encrypted).startsWith("v1.").doesNotContain("M12345678");
        assertThat(cipher.decrypt(encrypted, companyId, runId)).isEqualTo(plaintext);
        assertThatThrownBy(() -> cipher.decrypt(encrypted, UUID.randomUUID(), runId))
                .isInstanceOf(IllegalStateException.class);
    }
}
