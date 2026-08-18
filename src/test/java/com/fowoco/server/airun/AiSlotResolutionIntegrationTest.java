package com.fowoco.server.airun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.AiConfidenceSource;
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

        AiSlotResolution result = resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("같은이름")
        );

        assertThat(result.worker().workerRef()).isEqualTo(WORKER_A);
        assertThat(result.resolvedFields()).containsEntry("stay_expiry_date", "2026-09-30");
        assertThat(result.resolvedFields()).doesNotContainValue("2099-12-31");
    }

    @Test
    void normalizedLookupDoesNotReadAnotherCompanyCandidate() {
        insertCompany(COMPANY_A, "사업장 A");
        insertCompany(COMPANY_B, "사업장 B");
        insertWorker(WORKER_A, COMPANY_A, "응우옌 반 안", "2026-09-30");
        insertWorker(WORKER_B, COMPANY_B, "응우옌반안", "2099-12-31");

        AiSlotResolution result = resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("응 우 옌 반 안")
        );

        assertThat(result.worker().workerRef()).isEqualTo(WORKER_A);
        assertThat(result.resolvedFields()).containsEntry("stay_expiry_date", "2026-09-30");
        assertThat(result.resolvedFields()).doesNotContainValue("2099-12-31");
    }

    @Test
    void duplicateDisplayNameInsideTheSameCompanyIsReportedAsAmbiguous() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "동명이인", "2026-09-30");
        insertWorker(WORKER_A_DUPLICATE, COMPANY_A, "동명이인", "2027-09-30");

        assertThatThrownBy(() -> resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("동명이인")
        ))
                .isInstanceOfSatisfying(AiContextResolutionException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(
                                AiContextResolutionFailureCode.TARGET_AMBIGUOUS
                        )
                );
    }

    @Test
    void resolvesSpacingSeparatorAndCaseVariantsInsideTheCurrentCompany() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "응우옌 반 안", "2026-09-30");

        AiSlotResolution korean = resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("응 우 옌-반_안")
        );

        assertThat(korean.worker().workerRef()).isEqualTo(WORKER_A);

        insertWorker(WORKER_A_DUPLICATE, COMPANY_A, "Nguyen Van An", "2026-09-30");

        AiSlotResolution romanized = resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("NGUYEN-VAN_AN")
        );

        assertThat(romanized.worker().workerRef()).isEqualTo(WORKER_A_DUPLICATE);
    }

    @Test
    void exactMatchWinsBeforeNormalizedCandidates() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "응우옌 반 안", "2026-09-30");
        insertWorker(WORKER_A_DUPLICATE, COMPANY_A, "응우옌반안", "2027-09-30");

        AiSlotResolution result = resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("응우옌 반 안")
        );

        assertThat(result.worker().workerRef()).isEqualTo(WORKER_A);
    }

    @Test
    void multipleNormalizedCandidatesAreReportedAsAmbiguous() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "응우옌 반 안", "2026-09-30");
        insertWorker(WORKER_A_DUPLICATE, COMPANY_A, "응우옌반안", "2027-09-30");

        assertThatThrownBy(() -> resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("응 우 옌 반 안")
        ))
                .isInstanceOfSatisfying(AiContextResolutionException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(
                                AiContextResolutionFailureCode.TARGET_AMBIGUOUS
                        )
                );
    }

    @Test
    void resolvesUniqueNormalizedPrefixInsideTheCurrentCompany() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "응웬반A", "2026-09-30");

        AiSlotResolution result = resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("응웬반")
        );

        assertThat(result.worker().workerRef()).isEqualTo(WORKER_A);
    }

    @Test
    void resolvesWorkerWhenAgentReturnsSentenceStartingWithTheDisplayName() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "응웬반A", "2026-09-30");

        AiSlotResolution result = resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("응웬반A가 3년 만료 예정이야.")
        );

        assertThat(result.worker().workerRef()).isEqualTo(WORKER_A);
    }

    @Test
    void longestDisplayNameWinsWhenAgentReturnsANamePrefixedSentence() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "응웬반", "2026-09-30");
        insertWorker(WORKER_A_DUPLICATE, COMPANY_A, "응웬반A", "2027-09-30");

        AiSlotResolution result = resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("응웬반A가 3년 만료 예정이야.")
        );

        assertThat(result.worker().workerRef()).isEqualTo(WORKER_A_DUPLICATE);
    }

    @Test
    void multipleNormalizedPrefixCandidatesAreReportedAsAmbiguous() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "응웬반A", "2026-09-30");
        insertWorker(WORKER_A_DUPLICATE, COMPANY_A, "응웬반B", "2027-09-30");

        assertThatThrownBy(() -> resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("응웬반")
        ))
                .isInstanceOfSatisfying(AiContextResolutionException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(
                                AiContextResolutionFailureCode.TARGET_AMBIGUOUS
                        )
                );
    }

    @Test
    void shortPrefixIsNotUsedForWorkerResolution() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "응웬반A", "2026-09-30");

        assertThatThrownBy(() -> resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("응")
        ))
                .isInstanceOfSatisfying(AiContextResolutionException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(
                                AiContextResolutionFailureCode.TARGET_NOT_FOUND
                        )
                );
    }

    @Test
    void separatorOnlyTargetIsReportedAsNotFound() {
        insertCompany(COMPANY_A, "사업장 A");
        insertWorker(WORKER_A, COMPANY_A, "응우옌 반 안", "2026-09-30");

        assertThatThrownBy(() -> resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("- _ .")
        ))
                .isInstanceOfSatisfying(AiContextResolutionException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(
                                AiContextResolutionFailureCode.TARGET_NOT_FOUND
                        )
                );
    }

    @Test
    void resolvesLatestIdentityDocumentStatusesWithoutReadingAnotherCompany() {
        insertCompany(COMPANY_A, "사업장 A");
        insertCompany(COMPANY_B, "사업장 B");
        insertWorker(WORKER_A, COMPANY_A, "문서상태근로자", "2026-09-30");
        insertWorker(WORKER_B, COMPANY_B, "문서상태근로자", "2099-12-31");
        insertDocument(
                UUID.fromString("73000000-0000-0000-0000-000000000001"),
                WORKER_A,
                COMPANY_A,
                "PASSPORT_COPY",
                "SUBMITTED",
                "2026-08-01T00:00:00Z"
        );
        insertDocument(
                UUID.fromString("73000000-0000-0000-0000-000000000002"),
                WORKER_A,
                COMPANY_A,
                "PASSPORT_COPY",
                "VERIFIED",
                "2026-08-02T00:00:00Z"
        );
        insertDocument(
                UUID.fromString("73000000-0000-0000-0000-000000000003"),
                WORKER_B,
                COMPANY_B,
                "ARC",
                "VERIFIED",
                "2026-08-03T00:00:00Z"
        );

        AiSlotResolution result = resolutionTransaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement("문서상태근로자", List.of("passport_status", "arc_status"))
        );

        assertThat(result.resolvedFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "passport_status", "VERIFIED",
                "arc_status", "MISSING"
        ));
    }

    private AiContextRequirement requirement(String displayName) {
        return requirement(displayName, List.of("worker_id", "stay_expiry_date"));
    }

    private AiContextRequirement requirement(String displayName, List<String> fieldKeys) {
        return new AiContextRequirement(
                "EXPIRY_RENEWAL",
                new BigDecimal("0.94"),
                displayName,
                Map.of(),
                fieldKeys,
                "WF-STY-001",
                "체류연장 준비",
                AiConfidenceSource.MODEL,
                null
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
            String status,
            String updatedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO worker_document (
                    worker_document_id, worker_id, company_id, document_type,
                    submission_status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMP WITH TIME ZONE),
                          CAST(? AS TIMESTAMP WITH TIME ZONE), 0)
                """,
                documentId,
                workerId,
                companyId,
                documentType,
                status,
                updatedAt,
                updatedAt
        );
    }
}
