package com.fowoco.server.file.support;
import com.fowoco.server.file.application.port.FileStorage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트 전용 FileStorage. 실제 디스크에 쓰지 않고 메모리에만 기록.
 */

public class FakeFileStorage implements FileStorage {
    private final Map<String, byte[]> storedContents = new ConcurrentHashMap<>();
    @Override
    public void store(String storageKey, InputStream content, long size, String mimeType) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            content.transferTo(buffer);
            storedContents.put(storageKey, buffer.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
    public boolean contains(String storageKey) {
        return storedContents.containsKey(storageKey);
    }
    public byte[] contentOf(String storageKey) {
        return storedContents.get(storageKey);
    }
    public void clear() {
        storedContents.clear();
    }
}
