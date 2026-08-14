package com.fowoco.server.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.FileCreateCommand;
import com.fowoco.server.file.application.FileService;
import com.fowoco.server.file.application.port.FileStorage;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest
class FileRollbackIntegrationTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("74000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("74100000-0000-0000-0000-000000000001");
    private static final ActorContext ACTOR =
            new ActorContext(ACTOR_ID, COMPANY_ID, Set.of(UserRole.HR));
    private static final RequestMetadata METADATA =
            new RequestMetadata("file-rollback-request", null);

    @Autowired
    private FileService fileService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private FileStorage fileStorage;

    @MockitoBean
    private StoredFileRepository storedFileRepository;

    @MockitoBean
    private AuditEventRepository auditRepository;

    private final Map<String, byte[]> storedContents = new HashMap<>();

    @BeforeEach
    void setUpStorage() throws Exception {
        reset(fileStorage, storedFileRepository, auditRepository);
        storedContents.clear();
        doAnswer(invocation -> {
            String storageKey = invocation.getArgument(0);
            InputStream content = invocation.getArgument(1);
            storedContents.put(storageKey, content.readAllBytes());
            return null;
        }).when(fileStorage).store(anyString(), any(InputStream.class), anyLong(), anyString());
        doAnswer(invocation -> {
            storedContents.remove(invocation.<String>getArgument(0));
            return null;
        }).when(fileStorage).deleteIfExists(anyString());
    }

    @Test
    void keepsStoredFileAfterCommit() {
        StoredFile storedFile = fileService.upload(command(), ACTOR, METADATA);

        assertThat(storedContents).containsKey(storedFile.storageKey());
        verify(fileStorage, never()).deleteIfExists(storedFile.storageKey());
    }

    @Test
    void cleansStoredFileWhenStoredFileInsertFails() {
        RuntimeException originalFailure = new IllegalStateException("stored file insert failed");
        doThrow(originalFailure).when(storedFileRepository).insert(any());

        assertThatThrownBy(() -> fileService.upload(command(), ACTOR, METADATA))
                .isSameAs(originalFailure);

        assertThat(storedContents).isEmpty();
    }

    @Test
    void cleansStoredFileWhenAuditAppendFails() {
        RuntimeException originalFailure = new IllegalStateException("audit append failed");
        doThrow(originalFailure).when(auditRepository).append(any());

        assertThatThrownBy(() -> fileService.upload(command(), ACTOR, METADATA))
                .isSameAs(originalFailure);

        assertThat(storedContents).isEmpty();
    }

    @Test
    void cleansStoredFileWhenOuterTransactionFailsAfterUploadReturns() {
        RuntimeException originalFailure = new IllegalStateException("outer transaction failed");
        AtomicReference<String> storageKey = new AtomicReference<>();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            StoredFile storedFile = fileService.upload(command(), ACTOR, METADATA);
            storageKey.set(storedFile.storageKey());
            assertThat(storedContents).containsKey(storedFile.storageKey());
            throw originalFailure;
        })).isSameAs(originalFailure);

        assertThat(storageKey.get()).isNotNull();
        assertThat(storedContents).doesNotContainKey(storageKey.get());
    }

    @Test
    void cleanupFailureDoesNotReplaceOriginalTransactionFailure() {
        RuntimeException originalFailure = new IllegalStateException("stored file insert failed");
        RuntimeException cleanupFailure = new IllegalStateException("file cleanup failed");
        doThrow(originalFailure).when(storedFileRepository).insert(any());
        doThrow(cleanupFailure).when(fileStorage).deleteIfExists(anyString());

        assertThatThrownBy(() -> fileService.upload(command(), ACTOR, METADATA))
                .isSameAs(originalFailure);

        assertThat(storedContents).hasSize(1);
    }

    private FileCreateCommand command() {
        byte[] content = "rollback-safe-content".getBytes(StandardCharsets.UTF_8);
        return new FileCreateCommand(
                COMPANY_ID,
                "rollback-test.pdf",
                "application/pdf",
                content.length,
                "GENERAL",
                null,
                null,
                new ByteArrayInputStream(content)
        );
    }
}
