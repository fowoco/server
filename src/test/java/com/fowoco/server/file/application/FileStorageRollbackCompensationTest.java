package com.fowoco.server.file.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.port.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class FileStorageRollbackCompensationTest {

    private static final String STORAGE_KEY = "server-generated-storage-key";
    private static final RequestMetadata METADATA = new RequestMetadata("request-1", null);
    private static final String ACTION = "file_upload";

    private FileStorage fileStorage;
    private FileStorageRollbackCompensation compensation;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        fileStorage = mock(FileStorage.class);
        compensation = new FileStorageRollbackCompensation(fileStorage);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void keepsFileAfterCommit() {
        FileStorageRollbackCompensation.Registration registration =
                compensation.register(STORAGE_KEY, METADATA, ACTION);
        registration.markCreated();

        synchronization().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(fileStorage, never()).deleteIfExists(STORAGE_KEY);
    }

    @Test
    void deletesFileAfterRollback() {
        FileStorageRollbackCompensation.Registration registration =
                compensation.register(STORAGE_KEY, METADATA, ACTION);
        registration.markCreated();

        synchronization().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileStorage).deleteIfExists(STORAGE_KEY);
    }

    @Test
    void keepsFileWhenStoreDidNotCreateIt() {
        compensation.register(STORAGE_KEY, METADATA, ACTION);

        synchronization().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileStorage, never()).deleteIfExists(STORAGE_KEY);
    }

    @Test
    void keepsFileWhenTransactionCompletionIsUnknown() {
        FileStorageRollbackCompensation.Registration registration =
                compensation.register(STORAGE_KEY, METADATA, ACTION);
        registration.markCreated();

        synchronization().afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

        verify(fileStorage, never()).deleteIfExists(STORAGE_KEY);
    }

    @Test
    void cleanupFailureDoesNotEscapeTransactionCompletionCallback() {
        RuntimeException cleanupFailure = new IllegalStateException("cleanup unavailable");
        doThrow(cleanupFailure).when(fileStorage).deleteIfExists(STORAGE_KEY);
        FileStorageRollbackCompensation.Registration registration =
                compensation.register(STORAGE_KEY, METADATA, ACTION);
        registration.markCreated();

        assertThatCode(() -> synchronization().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK))
                .doesNotThrowAnyException();

        verify(fileStorage).deleteIfExists(STORAGE_KEY);
    }

    @Test
    void rejectsRegistrationWithoutActiveTransactionSynchronization() {
        TransactionSynchronizationManager.clear();

        assertThatThrownBy(() -> compensation.register(STORAGE_KEY, METADATA, ACTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction synchronization");

        verify(fileStorage, never()).deleteIfExists(STORAGE_KEY);
    }

    private TransactionSynchronization synchronization() {
        return TransactionSynchronizationManager.getSynchronizations().get(0);
    }
}
