package com.fowoco.server.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.auth.infrastructure.crypto.AccountPiiCipher;
import com.fowoco.server.auth.infrastructure.crypto.AccountPiiCryptoConfiguration;
import com.fowoco.server.auth.infrastructure.crypto.AccountPiiProperties;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountJpaEntityPiiRotationTest {

    private static final UUID COMPANY_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID USER_ID = UUID.fromString(
            "11000000-0000-0000-0000-000000000001"
    );
    private static final String PHONE = "010-1234-5678";

    @Test
    void reEncryptsCiphertextWithCurrentKeyWhenAccountIsRead() {
        byte[] oldKey = new byte[32];
        byte[] currentKey = new byte[32];
        currentKey[0] = 1;
        AccountPiiCipher oldCipher = cipher("pii-v1", oldKey, "");
        UserAccountJpaEntity entity = UserAccountJpaEntity.fromDomain(
                UserAccount.create(
                        USER_ID,
                        COMPANY_ID,
                        "담당자",
                        PHONE,
                        "owner@example.com",
                        "password-hash",
                        UserRole.ADMIN,
                        Instant.parse("2026-08-19T00:00:00Z")
                ),
                oldCipher
        );

        AccountPiiCipher rotatingCipher = cipher(
                "pii-v2",
                currentKey,
                "pii-v1=" + Base64.getEncoder().encodeToString(oldKey)
        );
        assertThat(entity.toDomain(rotatingCipher).phone()).isEqualTo(PHONE);

        AccountPiiCipher currentOnlyCipher = cipher("pii-v2", currentKey, "");
        assertThat(entity.toDomain(currentOnlyCipher).phone()).isEqualTo(PHONE);
    }

    private AccountPiiCipher cipher(
            String currentVersion,
            byte[] currentKey,
            String previousKeys
    ) {
        AccountPiiProperties properties = new AccountPiiProperties();
        properties.setEnabled(true);
        properties.setCurrentKeyVersion(currentVersion);
        properties.setCurrentKeyBase64(Base64.getEncoder().encodeToString(currentKey));
        properties.setDecryptionKeys(previousKeys);
        return new AccountPiiCryptoConfiguration().accountPiiCipher(properties);
    }
}
