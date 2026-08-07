package com.fowoco.server.document.infrastructure.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document.ocr")
public final class DocumentOcrProperties {

    private boolean enabled;
    private String encryptionKeyBase64;
    private String keyVersion = "local-v1";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEncryptionKeyBase64() {
        return encryptionKeyBase64;
    }

    public void setEncryptionKeyBase64(String encryptionKeyBase64) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }

    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            throw new IllegalStateException(
                    "OCR_RESULT_ENCRYPTION_KEY_BASE64 must be configured when document OCR is enabled"
            );
        }
        if (keyVersion == null || keyVersion.isBlank() || keyVersion.length() > 60) {
            throw new IllegalStateException("OCR_RESULT_KEY_VERSION must be 1 to 60 characters");
        }
    }
}
