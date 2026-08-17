package com.fowoco.server.file.application;

import java.io.InputStream;
import java.util.Objects;

public record FilePreviewResult(String fileName, String mimeType, long size, InputStream content) {

    public FilePreviewResult {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType must not be blank");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        Objects.requireNonNull(content, "content must not be null");
    }
}
