package com.fowoco.server.worker.application;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Produces the conservative lookup key used only after exact Worker display-name lookup fails.
 * It intentionally does not perform fuzzy or phonetic matching.
 */
@Component
public class WorkerDisplayNameNormalizer {

    public String normalize(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }

        String normalized = Normalizer.normalize(displayName.strip(), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
        StringBuilder lookupKey = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(lookupKey::appendCodePoint);

        if (lookupKey.isEmpty()) {
            throw new IllegalArgumentException("displayName must contain a letter or digit");
        }
        return lookupKey.toString();
    }
}
