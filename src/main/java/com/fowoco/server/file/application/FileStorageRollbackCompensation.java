package com.fowoco.server.file.application;

import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.port.FileStorage;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class FileStorageRollbackCompensation {

    private static final Logger log = LoggerFactory.getLogger(FileStorageRollbackCompensation.class);
    private static final String COMPLETION_PHASE = "transaction_after_completion";

    private final FileStorage fileStorage;

    public FileStorageRollbackCompensation(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public void register(String storageKey, RequestMetadata metadata, String action) {
        requireText(storageKey, "storageKey");
        Objects.requireNonNull(metadata, "metadata must not be null");
        requireText(action, "action");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "File storage rollback compensation requires an active transaction synchronization."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                handleCompletion(status, storageKey, metadata.requestId(), action);
            }
        });
    }

    private void handleCompletion(int status, String storageKey, String requestId, String action) {
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            return;
        }
        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
            cleanup(storageKey, requestId, action);
            return;
        }
        log.warn(
                "event=file_storage_transaction_completion status=UNKNOWN request_id={} action={} "
                        + "storage={} phase={} storage_key={} reconciliation_required=true completion_status={}",
                requestId,
                action,
                storageName(),
                COMPLETION_PHASE,
                storageKey,
                status
        );
    }

    private void cleanup(String storageKey, String requestId, String action) {
        log.info(
                "event=file_storage_cleanup status=ATTEMPT request_id={} action={} "
                        + "storage={} phase={} storage_key={}",
                requestId,
                action,
                storageName(),
                COMPLETION_PHASE,
                storageKey
        );
        try {
            fileStorage.deleteIfExists(storageKey);
            log.info(
                    "event=file_storage_cleanup status=SUCCEEDED request_id={} action={} "
                            + "storage={} phase={} storage_key={}",
                    requestId,
                    action,
                    storageName(),
                    COMPLETION_PHASE,
                    storageKey
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event=file_storage_cleanup status=FAILED request_id={} action={} "
                            + "storage={} phase={} storage_key={}",
                    requestId,
                    action,
                    storageName(),
                    COMPLETION_PHASE,
                    storageKey,
                    exception
            );
        }
    }

    private String storageName() {
        return fileStorage.getClass().getSimpleName();
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
