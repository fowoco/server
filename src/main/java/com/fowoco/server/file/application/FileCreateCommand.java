package com.fowoco.server.file.application;

import java.io.InputStream;
import java.util.UUID;

public final class FileCreateCommand {

    private final UUID companyId;
    private final String name;
    private final String mimeType;
    private final long size;
    private final String purpose;
    private final UUID taskId;
    private final UUID workerId;
    private final InputStream content;

    public FileCreateCommand(
            UUID companyId,
            String name,
            String mimeType,
            long size,
            String purpose,
            UUID taskId,
            UUID workerId,
            InputStream content
    ) {
        this.companyId = companyId;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
        this.purpose = purpose;
        this.taskId = taskId;
        this.workerId = workerId;
        this.content = content;
    }

    public UUID companyId() {
        return companyId;
    }

    public String name() {
        return name;
    }

    public String mimeType() {
        return mimeType;
    }

    public long size() {
        return size;
    }

    public String purpose() {
        return purpose;
    }

    public UUID taskId() {
        return taskId;
    }

    public UUID workerId() {
        return workerId;
    }

    public InputStream content() {
        return content;
    }
}
