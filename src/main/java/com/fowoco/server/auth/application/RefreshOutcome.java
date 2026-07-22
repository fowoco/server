package com.fowoco.server.auth.application;

import java.util.Objects;
import java.util.Optional;

public final class RefreshOutcome {

    public enum Status {
        SUCCEEDED,
        INVALID,
        REPLAY_DETECTED,
        SUBJECT_DISABLED
    }

    private final Status status;
    private final RefreshResult result;

    private RefreshOutcome(Status status, RefreshResult result) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.result = result;
        if ((status == Status.SUCCEEDED) != (result != null)) {
            throw new IllegalArgumentException("only a successful outcome can contain a result");
        }
    }

    public static RefreshOutcome succeeded(RefreshResult result) {
        return new RefreshOutcome(Status.SUCCEEDED, Objects.requireNonNull(result, "result must not be null"));
    }

    public static RefreshOutcome rejected(Status status) {
        if (status == Status.SUCCEEDED) {
            throw new IllegalArgumentException("a rejected outcome cannot use SUCCEEDED status");
        }
        return new RefreshOutcome(status, null);
    }

    public Status status() {
        return status;
    }

    public Optional<RefreshResult> result() {
        return Optional.ofNullable(result);
    }
}
