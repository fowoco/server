package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentRequestDraftSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.EvidenceSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ExternalSubmissionSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.worker.domain.DocumentType;
import java.util.List;
import java.util.UUID;

final class DemoTaskArtifactSeedCatalog {

    private DemoTaskArtifactSeedCatalog() {
    }

    static List<ExternalSubmissionSeed> demoExternalSubmissions(List<TaskSeed> tasks) {
        return List.of(
                submission(1, tasks, 4, "고용센터", "DEMO-EMPLOYMENT-EXT-001", 264),
                submission(2, tasks, 18, "고용센터", "DEMO-EMPLOYMENT-EXT-002", 312),
                submission(3, tasks, 19, "고용센터", "DEMO-RECONTRACT-REPORT-001", 360),
                submission(4, tasks, 20, "출입국·외국인청", "DEMO-STAY-EXT-001", 120),
                submission(5, tasks, 22, "고용센터", "DEMO-EMPLOYMENT-EXT-003", 144),
                submission(6, tasks, 23, "출입국·외국인청", "DEMO-STAY-EXT-002", 168)
        );
    }

    static List<EvidenceSeed> demoEvidence(List<TaskSeed> tasks) {
        return List.of(
                evidence(1, tasks, 5, EvidenceType.DOCUMENT, "서명된 재계약 문서 확인", 48),
                evidence(2, tasks, 5, EvidenceType.HR_CONFIRMATION, "계약 갱신 처리 완료 확인", 24),
                evidence(3, tasks, 20, EvidenceType.RECEIPT, "체류기간 연장 접수 확인", 48),
                evidence(4, tasks, 20, EvidenceType.OFFICIAL_RESULT, "체류기간 연장 승인 결과 확인", 12),
                evidence(5, tasks, 21, EvidenceType.DOCUMENT, "갱신 계약서 서명 상태 확인", 72),
                evidence(6, tasks, 21, EvidenceType.HR_CONFIRMATION, "재계약 인사 반영 완료", 24),
                evidence(7, tasks, 22, EvidenceType.RECEIPT, "고용허가기간 연장 접수 확인", 72),
                evidence(8, tasks, 22, EvidenceType.OFFICIAL_RESULT, "고용허가기간 연장 결과 확인", 24),
                evidence(9, tasks, 23, EvidenceType.RECEIPT, "체류 연장 신청 접수 확인", 96),
                evidence(10, tasks, 23, EvidenceType.OFFICIAL_RESULT, "체류 연장 최종 결과 확인", 24)
        );
    }

    static List<DocumentRequestDraftSeed> demoDocumentRequestDrafts(List<TaskSeed> tasks) {
        return List.of(
                draft(1, tasks, 3, "ne", List.of(DocumentType.PASSPORT_COPY),
                        "कृपया राहदानीको प्रतिलिपि तयार गर्नुहोस्।", 72),
                draft(2, tasks, 8, "vi", List.of(DocumentType.PASSPORT_COPY, DocumentType.ARC),
                        "Vui lòng chuẩn bị bản sao hộ chiếu và thẻ đăng ký người nước ngoài.", 36),
                draft(3, tasks, 10, "id", List.of(DocumentType.PASSPORT_COPY, DocumentType.ARC),
                        "Mohon siapkan salinan paspor dan kartu registrasi orang asing.", 72),
                draft(4, tasks, 11, "id", List.of(DocumentType.ARC),
                        "Mohon siapkan salinan terbaru kartu registrasi orang asing.", 120),
                draft(5, tasks, 12, "my", List.of(DocumentType.CONTRACT, DocumentType.PERMIT),
                        "ကျေးဇူးပြု၍ အလုပ်စာချုပ်နှင့် အလုပ်လုပ်ခွင့်စာရွက်စာတမ်းကို ပြင်ဆင်ပါ။", 144)
        );
    }

    private static ExternalSubmissionSeed submission(
            int number,
            List<TaskSeed> tasks,
            int taskNumber,
            String destination,
            String safeReference,
            int hoursAgo
    ) {
        return new ExternalSubmissionSeed(
                demoUuid("94500000-0000-0000-0000-000000000", number),
                tasks.get(taskNumber - 1).taskId(),
                destination,
                safeReference,
                hoursAgo
        );
    }

    private static EvidenceSeed evidence(
            int number,
            List<TaskSeed> tasks,
            int taskNumber,
            EvidenceType evidenceType,
            String note,
            int hoursAgo
    ) {
        return new EvidenceSeed(
                demoUuid("94600000-0000-0000-0000-000000000", number),
                tasks.get(taskNumber - 1).taskId(),
                evidenceType,
                note,
                hoursAgo
        );
    }

    private static DocumentRequestDraftSeed draft(
            int number,
            List<TaskSeed> tasks,
            int taskNumber,
            String language,
            List<DocumentType> documentTypes,
            String message,
            int hoursAgo
    ) {
        return new DocumentRequestDraftSeed(
                demoUuid("94700000-0000-0000-0000-000000000", number),
                tasks.get(taskNumber - 1).taskId(),
                language,
                documentTypes,
                message,
                hoursAgo
        );
    }

    private static UUID demoUuid(String prefix, int number) {
        return UUID.fromString(prefix + "%03d".formatted(number));
    }
}
