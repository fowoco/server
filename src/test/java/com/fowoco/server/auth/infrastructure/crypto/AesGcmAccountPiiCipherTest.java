package com.fowoco.server.auth.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AesGcmAccountPiiCipherTest {

    private static final UUID COMPANY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("11000000-0000-0000-0000-000000000001");

    @Test
    void encryptsWithTenantUserAndFieldBoundAuthenticatedData() {
        AesGcmAccountPiiCipher cipher = cipher(Map.of("test-v1", new byte[32]), "test-v1");

        AccountPiiCipher.EncryptedValue encrypted = cipher.encrypt(
                "010-1234-5678",
                COMPANY_ID,
                USER_ID,
                "phone"
        );

        assertThat(encrypted.ciphertext()).startsWith("v1.").doesNotContain("010-1234-5678");
        assertThat(encrypted.keyVersion()).isEqualTo("test-v1");
        assertThat(cipher.decrypt(
                encrypted.ciphertext(),
                encrypted.keyVersion(),
                COMPANY_ID,
                USER_ID,
                "phone"
        )).isEqualTo("010-1234-5678");
        assertThatThrownBy(() -> cipher.decrypt(
                encrypted.ciphertext(),
                encrypted.keyVersion(),
                UUID.randomUUID(),
                USER_ID,
                "phone"
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(
                encrypted.ciphertext(),
                encrypted.keyVersion(),
                COMPANY_ID,
                USER_ID,
                "email"
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decryptsPreviousKeyVersionDuringRotation() {
        byte[] previousKey = new byte[32];
        byte[] currentKey = new byte[32];
        currentKey[0] = 1;
        AesGcmAccountPiiCipher previous = cipher(Map.of("pii-v1", previousKey), "pii-v1");
        AccountPiiCipher.EncryptedValue encrypted = previous.encrypt(
                "010-9999-0000",
                COMPANY_ID,
                USER_ID,
                "phone"
        );
        AesGcmAccountPiiCipher rotated = cipher(
                Map.of("pii-v1", previousKey, "pii-v2", currentKey),
                "pii-v2"
        );

        assertThat(rotated.decrypt(
                encrypted.ciphertext(),
                encrypted.keyVersion(),
                COMPANY_ID,
                USER_ID,
                "phone"
        )).isEqualTo("010-9999-0000");
        assertThat(rotated.encrypt("010-9999-0000", COMPANY_ID, USER_ID, "phone").keyVersion())
                .isEqualTo("pii-v2");
    }

    @Test
    void rejectsTamperedCiphertextAndUnknownKeyVersion() {
        AesGcmAccountPiiCipher cipher = cipher(Map.of("test-v1", new byte[32]), "test-v1");
        AccountPiiCipher.EncryptedValue encrypted = cipher.encrypt(
                "010-1234-5678",
                COMPANY_ID,
                USER_ID,
                "phone"
        );
        String tampered = encrypted.ciphertext().substring(0, encrypted.ciphertext().length() - 1) + "A";

        assertThatThrownBy(() -> cipher.decrypt(
                tampered,
                encrypted.keyVersion(),
                COMPANY_ID,
                USER_ID,
                "phone"
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(
                encrypted.ciphertext(),
                "unknown-v1",
                COMPANY_ID,
                USER_ID,
                "phone"
        )).isInstanceOf(IllegalStateException.class);
    }

    private AesGcmAccountPiiCipher cipher(Map<String, byte[]> keys, String currentVersion) {
        return new AesGcmAccountPiiCipher(keys, currentVersion, new SecureRandom());
    }
}
