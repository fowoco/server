package com.fowoco.server.document.infrastructure.crypto;

import com.fowoco.server.document.application.port.OcrResultCipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class AesGcmOcrResultCipher implements OcrResultCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final String keyVersion;
    private final SecureRandom secureRandom;

    AesGcmOcrResultCipher(byte[] keyBytes, String keyVersion, SecureRandom secureRandom) {
        if (keyBytes.length != 32) {
            throw new IllegalStateException("OCR result encryption key must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes.clone(), "AES");
        this.keyVersion = Objects.requireNonNull(keyVersion, "keyVersion must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String keyVersion() {
        return keyVersion;
    }

    @Override
    public String encrypt(byte[] plaintext, UUID companyId, UUID ocrRunId) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(companyId, ocrRunId));
            byte[] encrypted = cipher.doFinal(plaintext);
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("OCR result encryption failed", exception);
        }
    }

    @Override
    public byte[] decrypt(String ciphertext, UUID companyId, UUID ocrRunId) {
        String[] parts = Objects.requireNonNull(ciphertext, "ciphertext must not be null").split("\\.", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalStateException("OCR result ciphertext format is invalid");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
            if (iv.length != IV_BYTES) {
                throw new IllegalStateException("OCR result ciphertext IV is invalid");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(companyId, ocrRunId));
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("OCR result decryption failed", exception);
        }
    }

    private byte[] aad(UUID companyId, UUID ocrRunId) {
        return (companyId + ":" + ocrRunId).getBytes(StandardCharsets.UTF_8);
    }
}
