package com.fowoco.server.approval.infrastructure.persistence;

import com.fowoco.server.approval.domain.EvidenceType;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataEvidenceJpaRepository extends JpaRepository<EvidenceJpaEntity, UUID> {

    @Query("""
            SELECT DISTINCT evidence.evidenceType
            FROM EvidenceJpaEntity evidence
            WHERE evidence.taskId = :taskId
              AND evidence.companyId = :companyId
            """)
    Set<EvidenceType> findTypesByTaskIdAndCompanyId(
            @Param("taskId") UUID taskId,
            @Param("companyId") UUID companyId
    );
}
