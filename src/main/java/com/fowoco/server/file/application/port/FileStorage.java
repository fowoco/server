package com.fowoco.server.file.application.port;

import java.io.InputStream;
import java.util.Optional;

public interface FileStorage {

    void store(String storageKey, InputStream content, long size, String mimeType);

    Optional<InputStream> open(String storageKey);
}
