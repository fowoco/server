package com.fowoco.server.airun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.airun.application.AiSlotResolution;
import com.fowoco.server.airun.application.AiSlotResolutionTransaction;
import com.fowoco.server.airun.application.error.AiContextResolutionException;
import com.fowoco.server.airun.application.error.AiContextResolutionFailureCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AiSlotResolutionIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("71000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_B = UUID.fromString("72000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A_DUPLICATE = UUID.fromString("72000000-0000-0000-0000-000000000003");
    private static final UUID PASSPORT_A = UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final UUID ARC_A = UUID.fromString("73000000-0000-0000-0000-000000000002");
    private static final UUID PASSPORT_B = UUID.fromString("73000000-0000-0000-0000-000000000003");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiSlotResolutionTransaction resolutionTransaction;

    @Test
    void sameDisplayNameInAnotherCompanyDoesNotAffectTenantScopedResolution() {
        insertCompany(COMPANY_A, "사업장 A");
        insertCompany(COMPANY_B, "사업장 B");
        insertWorker(WORKER_A, COMPANY_A, "같은이름", "2026-09-30");
        insertWorker(WORKER_B, COMPANY_B, "같은이름", "2099-12-31");
        insertDocument(PASSPORT_A, WORKER_A, COMPANY_A, "PASSPORT_COPY", "VERIFIED", "2027-09-30");
        insertDocument(ARC_A, WORKER_A, COMPANY_A, "ARC", "MISSING", null);
        insertDocument(PASSPORT_B, WORKER_B, COMPANY_B, "PASSPORT_COPY", "MISSING", null);

        AiSlotResolution result = resolutionTransaction.resolve(
                COMPANY_A,
                "0.2.0",
                requirement("같은이름")
        );

        assertThat(result.worker().workerRef()).isEqualTo(WORKER_A);
        assertThat(result.resolvedFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "worker_id", WORKER_A.toString(),
                "stay_expiry_date", "2026-09-30",
                "passport_copy_status", "VERIFIED",
                "passport_copy_expiry_date", "2027-09-30",
                "arc_status", "MISSING"
        ));
        assertThat(result.missingFieldKeys()).containsExactly("arc_expiry_date");
        assertThat(result.resolvedFields()).doesNotContainValue("2099-12-31");
    }

    @Test
    void duplicateDisplayNameInsideTheSameCompanyIsReportedAsAmbiguous() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "동명이인", "2026-09-30");
        insertWorker(WORKER_A_DUPLICATE, COMPANY_A, "동명이인", "2027-09-30");

        assertThatThrownBy(() -> resolutionTransaction.resolve(
                COMPANY_A,
                "0.2.0",
                requirement("동명이인")
        ))
                .isInstanceOfSatisfying(AiContextResolutionException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(
                                AiContextResolutionFailureCode.TARGET_AMBIGUOUS
                        )
                );
    }

    private AiContextRequirement requirement(String displayName) {
        return new AiContextRequirement(
                "EXPIRY_RENEWAL",
                new BigDecimal("0.94"),
                displayName,
                Map.of(),
                List.of(
                        "worker_id",
                        "stay_expiry_date",
                        "passport_copy_status",
                        "passport_copy_expiry_date",
                        "arc_status",
                        "arc_expiry_date"
                )
        );
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                "INSERT INTO company (company_id, name, status) VALUES (?, ?, 'ACTIVE')",
                companyId,
                name
        );
    }

    private void insertWorker(UUID workerId, UUID companyId, String displayName, String expiryDate) {
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, work_status, stay_expiry_date
                ) VALUES (?, ?, ?, 'ACTIVE', CAST(? AS DATE))
                """,
                workerId,
                companyId,
                displayName,
                expiryDate
        );
    }

    private void insertDocument(
            UUID documentId,
            UUID workerId,
            UUID companyId,
            String documentType,
            String submissionStatus,
            String expiryDate
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO worker_document (
                    worker_document_id, worker_id, company_id,
                    document_type, submission_status, expiry_date
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS DATE))
                """,
                documentId,
                workerId,
                companyId,
                documentType,
                submissionStatus,
                expiryDate
        );
    }
}
