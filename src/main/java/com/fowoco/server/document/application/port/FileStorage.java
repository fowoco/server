package com.fowoco.server.document.application.port;

import java.io.InputStream;

public interface FileStorage {

    void store(String storageKey, InputStream content, long size, String mimeType);
}
