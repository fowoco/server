package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.SubmissionStatus;
import java.util.Objects;

/**
 * Current Server-owned submission status for the identity documents used by AI workflows.
 */
public record WorkerIdentityDocumentStatuses(
        SubmissionStatus passportStatus,
        SubmissionStatus arcStatus
) {

    public WorkerIdentityDocumentStatuses {
        Objects.requireNonNull(passportStatus, "passportStatus must not be null");
        Objects.requireNonNull(arcStatus, "arcStatus must not be null");
    }

    public static WorkerIdentityDocumentStatuses missing() {
        return new WorkerIdentityDocumentStatuses(
                SubmissionStatus.MISSING,
                SubmissionStatus.MISSING
        );
    }

}
