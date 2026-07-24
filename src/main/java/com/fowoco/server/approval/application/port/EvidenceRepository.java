package com.fowoco.server.approval.application.port;

import com.fowoco.server.approval.domain.Evidence;
import java.util.UUID;

public interface EvidenceRepository {

    Evidence save(Evidence evidence);

    boolean existsByTaskIdAndCompanyId(UUID taskId, UUID companyId);
}
