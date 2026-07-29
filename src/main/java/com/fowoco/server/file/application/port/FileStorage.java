package com.fowoco.server.file.application.port;

import java.io.InputStream;

public interface FileStorage {

    void store(String storageKey, InputStream content, long size, String mimeType);
}
