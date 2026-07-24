package com.fowoco.server.approval.application;

import com.fowoco.server.approval.domain.EvidenceType;
import java.time.Instant;

public record RecordEvidenceCommand(
        EvidenceType evidenceType,
        String fileReference,
        String note,
        Instant recordedAt
) {
}
