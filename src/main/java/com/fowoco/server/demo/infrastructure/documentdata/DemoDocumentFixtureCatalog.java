package com.fowoco.server.demo.infrastructure.documentdata;

import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

final class DemoDocumentFixtureCatalog {

    static final UUID COMPANY_ID = uuid("90000000-0000-0000-0000-000000000001");
    static final UUID ADMIN_USER_ID = uuid("90000000-0000-0000-0000-000000000002");
    static final UUID GOLD_WORKER_ID = workerId(6);
    static final UUID PASSPORT_BIO_DOCUMENT_ID = documentId(1);
    static final UUID OCR_DOCUMENT_ID = documentId(3);
    static final UUID OCR_RUN_ID = uuid("95300000-0000-0000-0000-000000000001");
    static final UUID OCR_RUNTIME_REQUEST_ID = uuid("95310000-0000-0000-0000-000000000001");

    private static final List<DemoDocumentFixture> BASE_FIXTURES = List.of(
            passportImageFixture(1, 6, -365, 365,
                    "여권_인적사항면_응웬반A.png", "passport-bio.png",
                    "Passport biographical page",
                    new PassportIdentity(
                            "NGUYEN VAN AN",
                            "NGUYEN",
                            "VAN AN",
                            "VIET NAM",
                            "VN",
                            LocalDate.of(1995, 4, 12),
                            "M",
                            "DEMO-0001-NOT-VALID",
                            6
                    )),
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

    private static final List<DemoDocumentFixture> PASSPORT_COVERAGE_FIXTURES = List.of(
            passportCoverageFixture(101, 1, "리웨이", "LI WEI", "LI", "WEI",
                    "CHINA", "CN", LocalDate.of(1992, 3, 14), "M"),
            passportCoverageFixture(102, 2, "속 체아", "SOK CHEA", "SOK", "CHEA",
                    "CAMBODIA", "KH", LocalDate.of(1996, 7, 21), "M"),
            passportCoverageFixture(103, 3, "아르준 타파", "ARJUN THAPA", "THAPA", "ARJUN",
                    "NEPAL", "NP", LocalDate.of(1991, 11, 8), "M"),
            passportCoverageFixture(104, 4, "부디 산토소", "BUDI SANTOSO", "SANTOSO", "BUDI",
                    "INDONESIA", "ID", LocalDate.of(1994, 2, 17), "M"),
            passportCoverageFixture(105, 5, "마크 레예스", "MARK REYES", "REYES", "MARK",
                    "PHILIPPINES", "PH", LocalDate.of(1993, 9, 25), "M"),
            passportCoverageFixture(106, 7, "아디 수르야", "ADI SURYA", "SURYA", "ADI",
                    "INDONESIA", "ID", LocalDate.of(1997, 1, 19), "M"),
            passportCoverageFixture(107, 8, "바트 에르덴", "BAT ERDENE", "BAT", "ERDENE",
                    "MONGOLIA", "MN", LocalDate.of(1990, 5, 30), "M"),
            passportCoverageFixture(108, 9, "라니 위자야", "RANI WIJAYA", "WIJAYA", "RANI",
                    "INDONESIA", "ID", LocalDate.of(1998, 8, 11), "F"),
            passportCoverageFixture(109, 10, "민 아웅", "MIN AUNG", "MIN", "AUNG",
                    "MYANMAR", "MM", LocalDate.of(1995, 12, 4), "M"),
            passportCoverageFixture(110, 11, "파티마 누르", "FATIMAH NUR", "NUR", "FATIMAH",
                    "INDONESIA", "ID", LocalDate.of(1996, 4, 27), "F"),
            passportCoverageFixture(111, 12, "트란 티 마이", "TRAN THI MAI", "TRAN", "THI MAI",
                    "VIET NAM", "VN", LocalDate.of(1999, 6, 15), "F"),
            passportCoverageFixture(112, 13, "쩐 꾸옥 바오", "TRAN QUOC BAO", "TRAN", "QUOC BAO",
                    "VIET NAM", "VN", LocalDate.of(1994, 10, 2), "M"),
            passportCoverageFixture(113, 14, "아이다나 베크", "AIDANA BEK", "BEK", "AIDANA",
                    "KYRGYZSTAN", "KG", LocalDate.of(1997, 3, 9), "F"),
            passportCoverageFixture(114, 15, "찬다라 소쿤", "CHANDARA SOKUN", "SOKUN", "CHANDARA",
                    "CAMBODIA", "KH", LocalDate.of(1992, 7, 6), "M"),
            passportCoverageFixture(115, 16, "니말 페레라", "NIMAL PERERA", "PERERA", "NIMAL",
                    "SRI LANKA", "LK", LocalDate.of(1991, 1, 23), "M"),
            passportCoverageFixture(116, 17, "알리 칸", "ALI KHAN", "KHAN", "ALI",
                    "PAKISTAN", "PK", LocalDate.of(1993, 5, 18), "M"),
            passportCoverageFixture(117, 18, "모하메드 라힘", "MOHAMMED RAHIM", "RAHIM", "MOHAMMED",
                    "BANGLADESH", "BD", LocalDate.of(1990, 9, 12), "M"),
            passportCoverageFixture(118, 19, "누르 아지자", "NUR AZIZAH", "AZIZAH", "NUR",
                    "INDONESIA", "ID", LocalDate.of(1998, 11, 29), "F"),
            passportCoverageFixture(119, 20, "아지즈 라히모프", "AZIZ RAKHIMOV", "RAKHIMOV", "AZIZ",
                    "UZBEKISTAN", "UZ", LocalDate.of(1992, 2, 8), "M"),
            passportCoverageFixture(120, 21, "알렉세이 이바노프", "ALEXEI IVANOV", "IVANOV", "ALEXEI",
                    "RUSSIAN FED.", "RU", LocalDate.of(1989, 12, 20), "M"),
            passportCoverageFixture(121, 22, "마리아 산토스", "MARIA SANTOS", "SANTOS", "MARIA",
                    "PHILIPPINES", "PH", LocalDate.of(1996, 8, 3), "F"),
            passportCoverageFixture(122, 23, "솜차이 차이야", "SOMCHAI CHAIYA", "CHAIYA", "SOMCHAI",
                    "THAILAND", "TH", LocalDate.of(1991, 6, 26), "M"),
            passportCoverageFixture(123, 24, "응우옌 티 란", "NGUYEN THI LAN", "NGUYEN", "THI LAN",
                    "VIET NAM", "VN", LocalDate.of(1997, 10, 14), "F"),
            passportCoverageFixture(124, 25, "데위 사푸트리", "DEWI SAPUTRI", "SAPUTRI", "DEWI",
                    "INDONESIA", "ID", LocalDate.of(1995, 3, 31), "F"),
            passportCoverageFixture(125, 26, "조제 다 코스타", "JOSE DA COSTA", "DA COSTA", "JOSE",
                    "TIMOR-LESTE", "TL", LocalDate.of(1990, 7, 17), "M"),
            passportCoverageFixture(126, 27, "압둘 카림", "ABDUL KARIM", "KARIM", "ABDUL",
                    "BANGLADESH", "BD", LocalDate.of(1994, 4, 5), "M"),
            passportCoverageFixture(127, 28, "비벡 타파", "BIBEK THAPA", "THAPA", "BIBEK",
                    "NEPAL", "NP", LocalDate.of(1993, 1, 28), "M")
    );

    private static final List<DemoDocumentFixture> FIXTURES = Stream.concat(
            BASE_FIXTURES.stream(), PASSPORT_COVERAGE_FIXTURES.stream()
    ).toList();

    private DemoDocumentFixtureCatalog() {
    }

    static List<DemoDocumentFixture> fixtures() {
        return FIXTURES;
    }

    static List<DemoDocumentFixture> passportCoverageFixtures() {
        return PASSPORT_COVERAGE_FIXTURES;
    }

    private static DemoDocumentFixture passportCoverageFixture(
            int documentNumber,
            int workerNumber,
            String displayName,
            String englishName,
            String surname,
            String givenNames,
            String nationality,
            String nationalityCode,
            LocalDate birthDate,
            String sex
    ) {
        return passportImageFixture(
                documentNumber,
                workerNumber,
                -180 - workerNumber,
                540 + workerNumber * 7,
                "여권사본_%s.png".formatted(displayName.replace(" ", "")),
                "passport-copy-worker-%02d.png".formatted(workerNumber),
                "Passport copy - " + englishName,
                identity(
                        englishName,
                        surname,
                        givenNames,
                        nationality,
                        nationalityCode,
                        birthDate,
                        sex,
                        workerNumber
                )
        );
    }

    private static DemoDocumentFixture passportImageFixture(
            int documentNumber,
            int workerNumber,
            Integer issueDays,
            Integer expiryDays,
            String originalFilename,
            String storageFilename,
            String title,
            PassportIdentity passportIdentity
    ) {
        return new DemoDocumentFixture(
                documentId(documentNumber),
                fileId(documentNumber),
                workerId(workerNumber),
                null,
                DocumentType.PASSPORT_COPY,
                SubmissionStatus.VERIFIED,
                issueDays,
                expiryDays,
                originalFilename,
                storageFilename,
                "image/png",
                FixtureFormat.PNG,
                title,
                destination(DocumentType.PASSPORT_COPY),
                "DEMO/SAMPLE fixture - not for official submission",
                passportIdentity
        );
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
                "DEMO/SAMPLE fixture - not for official submission",
                null
        );
    }

    private static PassportIdentity identity(
            String englishName,
            String surname,
            String givenNames,
            String nationality,
            String nationalityCode,
            LocalDate birthDate,
            String sex,
            int portraitSeed
    ) {
        return new PassportIdentity(
                englishName,
                surname,
                givenNames,
                nationality,
                nationalityCode,
                birthDate,
                sex,
                "DEMO-%02d-NOT-VALID".formatted(portraitSeed),
                portraitSeed
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
            String note,
            PassportIdentity passportIdentity
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

    record PassportIdentity(
            String englishName,
            String surname,
            String givenNames,
            String nationality,
            String nationalityCode,
            LocalDate birthDate,
            String sex,
            String documentNumber,
            int portraitSeed
    ) {
    }
}
