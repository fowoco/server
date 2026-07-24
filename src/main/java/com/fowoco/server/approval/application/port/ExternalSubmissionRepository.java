package com.fowoco.server.approval.application.port;

import com.fowoco.server.approval.domain.ExternalSubmission;
import java.util.UUID;

public interface ExternalSubmissionRepository {

    ExternalSubmission save(ExternalSubmission submission);

    boolean existsByTaskIdAndCompanyId(UUID taskId, UUID companyId);
}
