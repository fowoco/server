package com.fowoco.server.demo.infrastructure.documentdata;

import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.util.List;
import java.util.UUID;

final class DemoDocumentFixtureCatalog {

    static final UUID COMPANY_ID = uuid("90000000-0000-0000-0000-000000000001");
    static final UUID ADMIN_USER_ID = uuid("90000000-0000-0000-0000-000000000002");
    static final UUID GOLD_WORKER_ID = workerId(6);
    static final UUID PASSPORT_BIO_DOCUMENT_ID = documentId(1);
    static final UUID OCR_DOCUMENT_ID = documentId(3);
    static final UUID OCR_RUN_ID = uuid("95300000-0000-0000-0000-000000000001");
    static final UUID OCR_RUNTIME_REQUEST_ID = uuid("95310000-0000-0000-0000-000000000001");

    private static final List<DemoDocumentFixture> FIXTURES = List.of(
            fixture(1, 6, null, DocumentType.PASSPORT_COPY, SubmissionStatus.VERIFIED,
                    -365, 365, "여권_인적사항면_응웬반A.png", "passport-bio.png",
                    "image/png", FixtureFormat.PNG, "Passport biographical page"),
            fixture(2, 6, null, DocumentType.PASSPORT_COPY, SubmissionStatus.VERIFIED,
                    -365, 365, "여권_사본_응웬반A.pdf", "passport-copy.pdf",
                    "application/pdf", FixtureFormat.PDF, "Passport copy"),
            fixture(3, 6, null, DocumentType.ARC, SubmissionStatus.SUBMITTED,
                    -180, 270, "외국인등록증_앞면_응웬반A.png", "arc-front.png",
                    "image/png", FixtureFormat.PNG, "ARC front - OCR review required"),
            fixture(4, 6, null, DocumentType.ARC, SubmissionStatus.SUBMITTED,
                    -180, 270, "외국인등록증_뒷면_응웬반A.jpg", "arc-back.jpg",
                    "image/jpeg", FixtureFormat.JPEG, "ARC back - OCR review required"),
            fixture(5, 6, null, DocumentType.ARC, SubmissionStatus.VERIFIED,
                    -180, 14, "외국인등록증_통합사본_응웬반A.pdf", "arc-combined.pdf",
                    "application/pdf", FixtureFormat.PDF, "ARC combined copy - expiring soon"),
            fixture(6, 6, null, DocumentType.PERMIT, SubmissionStatus.VERIFIED,
                    -180, 180, "고용허가서_응웬반A.pdf", "employment-permit.pdf",
                    "application/pdf", FixtureFormat.PDF, "Employment permit"),
            fixture(7, 6, null, DocumentType.CONTRACT, SubmissionStatus.VERIFIED,
                    -30, 180, "표준근로계약서_응웬반A.pdf", "employment-contract.pdf",
                    "application/pdf", FixtureFormat.PDF, "Standard employment contract"),
            fixture(8, 6, null, DocumentType.CONTRACT, SubmissionStatus.VERIFIED,
                    -30, 180, "표준근로계약서_응웬반A.hwpx", "employment-contract.hwpx",
                    "application/hwp+zip", FixtureFormat.HWPX, "Standard employment contract HWPX"),
            fixture(9, 6, null, DocumentType.CONTRACT, SubmissionStatus.VERIFIED,
                    -30, 180, "표준근로계약서_응웬반A.hwp", "employment-contract.hwp",
                    "application/x-hwp", FixtureFormat.HWP, "Standard employment contract HWP"),
            fixture(10, 6, null, DocumentType.EMPLOYMENT_EXTENSION_APPLICATION, SubmissionStatus.DRAFT,
                    0, 60, "취업활동기간_연장신청서_초안_응웬반A.hwpx", "employment-extension-draft.hwpx",
                    "application/hwp+zip", FixtureFormat.HWPX, "Employment activity extension draft"),
            fixture(11, 6, null, DocumentType.INTEGRATED_APPLICATION, SubmissionStatus.DRAFT,
                    0, 60, "통합신청서_초안_응웬반A.hwpx", "integrated-application-draft.hwpx",
                    "application/hwp+zip", FixtureFormat.HWPX, "Integrated application draft"),
            fixture(12, 6, null, DocumentType.RESIDENCE_PROOF, SubmissionStatus.VERIFIED,
                    -45, 180, "체류지_입증자료_응웬반A.pdf", "residence-proof.pdf",
                    "application/pdf", FixtureFormat.PDF, "Residence proof"),
            fixture(13, 1, 1, DocumentType.CONTRACT, SubmissionStatus.VERIFIED,
                    -40, 220, "근로계약서_리웨이.pdf", "worker-01-contract.pdf",
                    "application/pdf", FixtureFormat.PDF, "Normal document state"),
            fixture(14, 2, 2, DocumentType.PERMIT, SubmissionStatus.VERIFIED,
                    -200, 10, "고용허가서_속체아.png", "worker-02-permit.png",
                    "image/png", FixtureFormat.PNG, "Expiring-soon document state"),
            fixture(15, 3, 3, DocumentType.PASSPORT_COPY, SubmissionStatus.VERIFIED,
                    -500, -10, "여권사본_아르준타파.pdf", "worker-03-expired-passport.pdf",
                    "application/pdf", FixtureFormat.PDF, "Expired document state"),
            fixture(16, 4, 4, DocumentType.RESIDENCE_PROOF, SubmissionStatus.MISSING,
                    null, null, null, null, null, FixtureFormat.NONE, "Required document missing")
    );

    private DemoDocumentFixtureCatalog() {
    }

    static List<DemoDocumentFixture> fixtures() {
        return FIXTURES;
    }

    private static DemoDocumentFixture fixture(
            int number,
            int workerNumber,
            Integer taskNumber,
            DocumentType documentType,
            SubmissionStatus status,
            Integer issueDays,
            Integer expiryDays,
            String originalFilename,
            String storageFilename,
            String contentType,
            FixtureFormat format,
            String title
    ) {
        return new DemoDocumentFixture(
                documentId(number),
                format == FixtureFormat.NONE ? null : fileId(number),
                workerId(workerNumber),
                taskNumber == null ? null : taskId(taskNumber),
                documentType,
                status,
                issueDays,
                expiryDays,
                originalFilename,
                storageFilename,
                contentType,
                format,
                title,
                destination(documentType),
                "DEMO/SAMPLE fixture - not for official submission"
        );
    }

    private static String destination(DocumentType type) {
        return switch (type) {
            case PASSPORT_COPY, ARC, INTEGRATED_APPLICATION, RESIDENCE_PROOF -> "체류기간 연장";
            case CONTRACT -> "근로계약 갱신";
            case PERMIT, EMPLOYMENT_EXTENSION_APPLICATION -> "취업활동기간 연장";
        };
    }

    static UUID documentId(int number) {
        return uuid("95200000-0000-0000-0000-000000000%03d".formatted(number));
    }

    private static UUID fileId(int number) {
        return uuid("94900000-0000-0000-0000-000000000%03d".formatted(number));
    }

    private static UUID workerId(int number) {
        return uuid("92000000-0000-0000-0000-000000000%03d".formatted(number));
    }

    private static UUID taskId(int number) {
        return uuid("94000000-0000-0000-0000-000000000%03d".formatted(number));
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    enum FixtureFormat {
        PNG,
        JPEG,
        PDF,
        HWP,
        HWPX,
        NONE
    }

    record DemoDocumentFixture(
            UUID documentId,
            UUID fileId,
            UUID workerId,
            UUID taskId,
            DocumentType documentType,
            SubmissionStatus status,
            Integer issueDays,
            Integer expiryDays,
            String originalFilename,
            String storageFilename,
            String contentType,
            FixtureFormat format,
            String title,
            String destination,
            String note
    ) {
        String storageKey() {
            if (fileId == null) {
                return null;
            }
            return "demo/%s/workers/%s/documents/%s/%s".formatted(
                    COMPANY_ID, workerId, documentId, storageFilename
            );
        }
    }
}
