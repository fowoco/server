package com.fowoco.server.aiintegration.application.ocr;

import java.util.Objects;

public record AiOcrFile(
        String fileName,
        String contentType,
        byte[] content
) {

    public AiOcrFile {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(content, "content must not be null");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public int size() {
        return content.length;
    }
}
