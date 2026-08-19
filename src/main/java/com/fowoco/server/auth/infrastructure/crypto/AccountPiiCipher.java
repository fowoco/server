package com.fowoco.server.auth.infrastructure.crypto;

import java.util.UUID;

public interface AccountPiiCipher {

    boolean isAvailable();

    String currentKeyVersion();

    default boolean requiresReEncryption(String keyVersion) {
        return isAvailable() && !currentKeyVersion().equals(keyVersion);
    }

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
