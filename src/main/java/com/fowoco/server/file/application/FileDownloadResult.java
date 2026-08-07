package com.fowoco.server.file.application;

import com.fowoco.server.file.domain.StoredFile;
import java.io.InputStream;
import java.util.Objects;

public record FileDownloadResult(StoredFile storedFile, InputStream content) {

    public FileDownloadResult {
        Objects.requireNonNull(storedFile, "storedFile must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
