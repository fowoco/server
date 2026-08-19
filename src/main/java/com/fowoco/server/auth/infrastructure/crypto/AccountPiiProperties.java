package com.fowoco.server.auth.infrastructure.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.pii")
public final class AccountPiiProperties {

    private boolean enabled;
    private String currentKeyBase64;
    private String currentKeyVersion = "local-v1";
    private String decryptionKeys = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCurrentKeyBase64() {
        return currentKeyBase64;
    }

    public void setCurrentKeyBase64(String currentKeyBase64) {
        this.currentKeyBase64 = currentKeyBase64;
    }

    public String getCurrentKeyVersion() {
        return currentKeyVersion;
    }

    public void setCurrentKeyVersion(String currentKeyVersion) {
        this.currentKeyVersion = currentKeyVersion;
    }

    public String getDecryptionKeys() {
        return decryptionKeys;
    }

    public void setDecryptionKeys(String decryptionKeys) {
        this.decryptionKeys = decryptionKeys;
    }

    void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        if (currentKeyBase64 == null || currentKeyBase64.isBlank()) {
            throw new IllegalStateException(
                    "PII_ENCRYPTION_KEY_BASE64 must be configured when account PII encryption is enabled"
            );
        }
        validateKeyVersion(currentKeyVersion, "PII_ENCRYPTION_KEY_VERSION");
    }

    static void validateKeyVersion(String value, String fieldName) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,60}")) {
            throw new IllegalStateException(fieldName + " must match [A-Za-z0-9._-]{1,60}");
        }
    }
}
