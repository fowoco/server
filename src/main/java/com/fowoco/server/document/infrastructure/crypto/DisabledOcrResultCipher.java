package com.fowoco.server.document.infrastructure.crypto;

import com.fowoco.server.document.application.port.OcrResultCipher;
import java.util.UUID;

final class DisabledOcrResultCipher implements OcrResultCipher {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String keyVersion() {
        throw new IllegalStateException("OCR result encryption is disabled");
    }

    @Override
    public String encrypt(byte[] plaintext, UUID companyId, UUID ocrRunId) {
        throw new IllegalStateException("OCR result encryption is disabled");
    }

    @Override
    public byte[] decrypt(String ciphertext, UUID companyId, UUID ocrRunId) {
        throw new IllegalStateException("OCR result encryption is disabled");
    }
}
