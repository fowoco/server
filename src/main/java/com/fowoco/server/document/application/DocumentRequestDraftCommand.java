package com.fowoco.server.document.application;

import com.fowoco.server.worker.domain.DocumentType;
import java.util.List;
import java.util.UUID;

public final class DocumentRequestDraftCommand {

    private final UUID taskId;
    private final UUID companyId;
    private final String language;
    private final List<DocumentType> documentTypes;
    private final String message;
    private final long expectedVersion;

    public DocumentRequestDraftCommand(
            UUID taskId,
            UUID companyId,
            String language,
            List<DocumentType> documentTypes,
            String message,
            long expectedVersion
    ) {
        this.taskId = taskId;
        this.companyId = companyId;
        this.language = language;
        this.documentTypes = documentTypes;
        this.message = message;
        this.expectedVersion = expectedVersion;
    }

    public UUID taskId() {
        return taskId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String language() {
        return language;
    }

    public List<DocumentType> documentTypes() {
        return documentTypes;
    }

    public String message() {
        return message;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
