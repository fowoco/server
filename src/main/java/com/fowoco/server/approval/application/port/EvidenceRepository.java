package com.fowoco.server.approval.application.port;

import com.fowoco.server.approval.domain.Evidence;
import com.fowoco.server.approval.domain.EvidenceType;
import java.util.Set;
import java.util.UUID;

public interface EvidenceRepository {

    Evidence save(Evidence evidence);

    Set<EvidenceType> findTypesByTaskIdAndCompanyId(UUID taskId, UUID companyId);
}
