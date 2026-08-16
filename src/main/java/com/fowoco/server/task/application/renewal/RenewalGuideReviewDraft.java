package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record RenewalGuideReviewDraft(
        String targetLanguage,
        String generationStatus,
        String standardKoreanText,
        String easyKoreanText,
        String translatedText,
        List<String> warningCodes
) {
    public RenewalGuideReviewDraft {
        warningCodes = warningCodes == null ? List.of() : List.copyOf(warningCodes);
    }

    static RenewalGuideReviewDraft from(RenewalRunResponse response) {
        if (!response.guideReviewRequired() || response.languageAssistant() == null) {
            return null;
        }
        Map<String, Object> language = response.languageAssistant();
        String standardKorean = stringValue(language, "standard_korean_text");
        String easyKorean = stringValue(language, "easy_korean_text");
        String translated = stringValue(language, "translated_text");
        if (standardKorean == null && easyKorean == null && translated == null) {
            return null;
        }
        return new RenewalGuideReviewDraft(
                stringValue(language, "target_language"),
                stringValue(language, "generation_status"),
                standardKorean,
                easyKorean,
                translated,
                warningCodes(language.get("warnings"))
        );
    }

    Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        putIfPresent(metadata, "target_language", targetLanguage);
        putIfPresent(metadata, "generation_status", generationStatus);
        putIfPresent(metadata, "standard_korean_text", standardKoreanText);
        putIfPresent(metadata, "easy_korean_text", easyKoreanText);
        putIfPresent(metadata, "translated_text", translatedText);
        metadata.put("warning_codes", warningCodes);
        return Map.copyOf(metadata);
    }

    private static String stringValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static List<String> warningCodes(Object value) {
        if (!(value instanceof List<?> warnings)) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (Object warning : warnings) {
            if (warning instanceof Map<?, ?> warningMap
                    && warningMap.get("code") instanceof String code
                    && !code.isBlank()) {
                codes.add(code);
            }
        }
        return List.copyOf(codes);
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
