package com.fowoco.server.auth.infrastructure.crypto;

import java.util.UUID;

final class DisabledAccountPiiCipher implements AccountPiiCipher {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public EncryptedValue encrypt(String plaintext, UUID companyId, UUID userId, String fieldName) {
        throw new IllegalStateException("account PII encryption is disabled");
    }

    @Override
    public String decrypt(
            String ciphertext,
            String keyVersion,
            UUID companyId,
            UUID userId,
            String fieldName
    ) {
        throw new IllegalStateException("account PII encryption is disabled");
    }
}
