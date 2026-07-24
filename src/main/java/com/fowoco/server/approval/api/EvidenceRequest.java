package com.fowoco.server.approval.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.approval.application.RecordEvidenceCommand;
import com.fowoco.server.approval.domain.EvidenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EvidenceRequest(
        @JsonProperty("evidence_type") @NotNull EvidenceType evidenceType,
        @JsonProperty("file_reference") @Size(max = 300) String fileReference,
        @Size(max = 500) String note,
        @JsonProperty("recorded_at") @PastOrPresent Instant recordedAt
) {

    public RecordEvidenceCommand toCommand() {
        return new RecordEvidenceCommand(evidenceType, fileReference, note, recordedAt);
    }
}
