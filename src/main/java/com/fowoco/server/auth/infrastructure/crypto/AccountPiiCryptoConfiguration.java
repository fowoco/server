package com.fowoco.server.auth.infrastructure.crypto;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountPiiProperties.class)
public class AccountPiiCryptoConfiguration {

    @Bean
    public AccountPiiCipher accountPiiCipher(AccountPiiProperties properties) {
        if (!properties.isEnabled()) {
            return new DisabledAccountPiiCipher();
        }
        properties.validateEnabledConfiguration();
        Map<String, byte[]> keys = parsePreviousKeys(properties.getDecryptionKeys());
        String currentVersion = properties.getCurrentKeyVersion().strip();
        byte[] currentKey = decodeKey(properties.getCurrentKeyBase64(), "PII_ENCRYPTION_KEY_BASE64");
        byte[] conflictingKey = keys.put(currentVersion, currentKey);
        if (conflictingKey != null && !Arrays.equals(conflictingKey, currentKey)) {
            throw new IllegalStateException("current PII key version conflicts with PII_DECRYPTION_KEYS");
        }
        return new AesGcmAccountPiiCipher(keys, currentVersion, new SecureRandom());
    }

    private Map<String, byte[]> parsePreviousKeys(String configuredKeys) {
        Map<String, byte[]> keys = new LinkedHashMap<>();
        if (configuredKeys == null || configuredKeys.isBlank()) {
            return keys;
        }
        for (String entry : configuredKeys.split(",")) {
            String[] pair = entry.strip().split("=", 2);
            if (pair.length != 2) {
                throw new IllegalStateException(
                        "PII_DECRYPTION_KEYS must use version=base64 entries separated by commas"
                );
            }
            String version = pair[0].strip();
            AccountPiiProperties.validateKeyVersion(version, "PII_DECRYPTION_KEYS version");
            byte[] previous = keys.put(version, decodeKey(pair[1], "PII_DECRYPTION_KEYS"));
            if (previous != null) {
                throw new IllegalStateException("PII_DECRYPTION_KEYS contains a duplicate version");
            }
        }
        return keys;
    }

    private byte[] decodeKey(String value, String fieldName) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value.strip());
            if (decoded.length != 32) {
                throw new IllegalStateException(fieldName + " must decode to 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(fieldName + " is not valid Base64", exception);
        }
    }
}
