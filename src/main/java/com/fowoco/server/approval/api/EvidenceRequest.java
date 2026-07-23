package com.fowoco.server.approval.api;

import com.fowoco.server.approval.application.RecordEvidenceCommand;
import com.fowoco.server.approval.domain.EvidenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EvidenceRequest(
        @NotNull EvidenceType evidenceType,
        @Size(max = 300) String fileReference,
        @Size(max = 500) String note,
        @PastOrPresent Instant recordedAt
) {

    public RecordEvidenceCommand toCommand() {
        return new RecordEvidenceCommand(evidenceType, fileReference, note, recordedAt);
    }
}
