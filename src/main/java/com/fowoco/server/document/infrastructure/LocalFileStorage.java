package com.fowoco.server.document.infrastructure;

import com.fowoco.server.document.application.port.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로컬 디스크에 파일을 저장하는 구현체. Controller/Service는 이 클래스의 존재를 모르고
 * FileStorage 인터페이스만 알아야 한다 (#13 보안 규칙 — Local 경로를 상위 계층에 노출하지 않음).
 * 나중에 S3 호환 저장소로 교체할 때는 이 클래스만 새 구현체로 바꿔치기하면 된다.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path rootDirectory;

    public LocalFileStorage(@Value("${app.file-storage.local-path}") String localPath) {
        this.rootDirectory = Path.of(localPath);
    }

    @Override
    public void store(String storageKey, InputStream content, long size, String mimeType) {
        try {
            Files.createDirectories(rootDirectory);
            Path target = rootDirectory.resolve(storageKey).normalize();
            if (!target.startsWith(rootDirectory)) {
                throw new IllegalArgumentException("storageKey must not escape the storage root");
            }
            Files.copy(content, target);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to store file: " + storageKey, exception);
        }
    }
}
