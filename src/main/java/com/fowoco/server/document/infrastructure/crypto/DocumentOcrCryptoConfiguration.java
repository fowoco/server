package com.fowoco.server.document.infrastructure.crypto;

import com.fowoco.server.document.application.port.OcrResultCipher;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocumentOcrProperties.class)
public class DocumentOcrCryptoConfiguration {

    @Bean
    public OcrResultCipher ocrResultCipher(DocumentOcrProperties properties) {
        if (!properties.isEnabled()) {
            return new DisabledOcrResultCipher();
        }
        properties.validateEnabledConfiguration();
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(properties.getEncryptionKeyBase64().strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("OCR result encryption key is not valid Base64", exception);
        }
        return new AesGcmOcrResultCipher(keyBytes, properties.getKeyVersion().strip(), new SecureRandom());
    }
}
