package com.fowoco.server.auth.infrastructure.crypto;

import java.util.UUID;

public interface AccountPiiCipher {

    boolean isAvailable();

    EncryptedValue encrypt(String plaintext, UUID companyId, UUID userId, String fieldName);

    String decrypt(
            String ciphertext,
            String keyVersion,
            UUID companyId,
            UUID userId,
            String fieldName
    );

    record EncryptedValue(String ciphertext, String keyVersion) {
    }
}
