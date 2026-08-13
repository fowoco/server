package com.fowoco.server.workerlink.application;

import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkerResponseSubmitCommand {

    private final String rawToken;
    private final WorkerResponseType responseType;
    private final String message;
    private final List<UUID> uploadIds;
    private final Map<String, String> answers;
    private final String idempotencyKey;

    public WorkerResponseSubmitCommand(
            String rawToken,
            WorkerResponseType responseType,
            String message,
            List<UUID> uploadIds,
            Map<String, String> answers,
            String idempotencyKey
    ) {
        this.rawToken = rawToken;
        this.responseType = responseType;
        this.message = message;
        this.uploadIds = uploadIds;
        this.answers = answers;
        this.idempotencyKey = idempotencyKey;
    }

    public String rawToken() {
        return rawToken;
    }

    public WorkerResponseType responseType() {
        return responseType;
    }

    public String message() {
        return message;
    }

    public List<UUID> uploadIds() {
        return uploadIds;
    }

    public Map<String, String> answers() {
        return answers;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
