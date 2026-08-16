package com.fowoco.server.workerlink.application;

import java.io.InputStream;

public final class WorkerLinkDocumentUploadCommand {

    private final String rawToken;
    private final String fileName;
    private final String mimeType;
    private final long size;
    private final String documentType;
    private final String clientRequestId;
    private final String idempotencyKey;
    private final InputStream content;

    public WorkerLinkDocumentUploadCommand(
            String rawToken,
            String fileName,
            String mimeType,
            long size,
            String documentType,
            String clientRequestId,
            String idempotencyKey,
            InputStream content
    ) {
        this.rawToken = rawToken;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.size = size;
        this.documentType = documentType;
        this.clientRequestId = clientRequestId;
        this.idempotencyKey = idempotencyKey;
        this.content = content;
    }

    public String rawToken() {
        return rawToken;
    }

    public String fileName() {
        return fileName;
    }

    public String mimeType() {
        return mimeType;
    }

    public long size() {
        return size;
    }

    public String documentType() {
        return documentType;
    }

    public String clientRequestId() {
        return clientRequestId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public InputStream content() {
        return content;
    }
}
