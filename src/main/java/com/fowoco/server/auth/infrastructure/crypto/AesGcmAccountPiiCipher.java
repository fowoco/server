package com.fowoco.server.auth.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class AesGcmAccountPiiCipher implements AccountPiiCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Map<String, SecretKeySpec> keys;
    private final String currentKeyVersion;
    private final SecureRandom secureRandom;

    AesGcmAccountPiiCipher(
            Map<String, byte[]> keyBytesByVersion,
            String currentKeyVersion,
            SecureRandom secureRandom
    ) {
        Objects.requireNonNull(keyBytesByVersion, "keyBytesByVersion must not be null");
        this.currentKeyVersion = requireText(currentKeyVersion, "currentKeyVersion");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        this.keys = keyBytesByVersion.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> requireText(entry.getKey(), "key version"),
                        entry -> secretKey(entry.getValue())
                ));
        if (!keys.containsKey(this.currentKeyVersion)) {
            throw new IllegalStateException("current PII encryption key version is missing from the keyring");
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String currentKeyVersion() {
        return currentKeyVersion;
    }

    @Override
    public EncryptedValue encrypt(
            String plaintext,
            UUID companyId,
            UUID userId,
            String fieldName
    ) {
        String value = Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keys.get(currentKeyVersion),
                    new GCMParameterSpec(TAG_BITS, iv)
            );
            cipher.updateAAD(aad(companyId, userId, fieldName));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String ciphertext = "v1."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
            return new EncryptedValue(ciphertext, currentKeyVersion);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("account PII encryption failed", exception);
        }
    }

    @Override
    public String decrypt(
            String ciphertext,
            String keyVersion,
            UUID companyId,
            UUID userId,
            String fieldName
    ) {
        SecretKeySpec key = keys.get(requireText(keyVersion, "keyVersion"));
        if (key == null) {
            throw new IllegalStateException("account PII decryption key version is unavailable");
        }
        String[] parts = requireText(ciphertext, "ciphertext").split("\\.", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalStateException("account PII ciphertext format is invalid");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
            if (iv.length != IV_BYTES) {
                throw new IllegalStateException("account PII ciphertext IV is invalid");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(companyId, userId, fieldName));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("account PII decryption failed", exception);
        }
    }

    private byte[] aad(UUID companyId, UUID userId, String fieldName) {
        return (Objects.requireNonNull(companyId, "companyId must not be null")
                + ":"
                + Objects.requireNonNull(userId, "userId must not be null")
                + ":user_account:"
                + requireText(fieldName, "fieldName"))
                .getBytes(StandardCharsets.UTF_8);
    }

    private SecretKeySpec secretKey(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        if (keyBytes.length != 32) {
            throw new IllegalStateException("account PII encryption keys must decode to 32 bytes");
        }
        return new SecretKeySpec(keyBytes.clone(), "AES");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
