package com.fowoco.server.document.application.port;

import java.util.UUID;

public interface OcrResultCipher {

    boolean isAvailable();

    String keyVersion();

    String encrypt(byte[] plaintext, UUID companyId, UUID ocrRunId);

    byte[] decrypt(String ciphertext, UUID companyId, UUID ocrRunId);
}
