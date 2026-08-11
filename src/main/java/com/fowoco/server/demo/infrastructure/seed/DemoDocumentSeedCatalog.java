package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class DemoDocumentSeedCatalog {

    private static final Set<Integer> MISSING_ADDITION_INDICES =
            Set.of(0, 8, 14, 18, 24, 30, 36, 42, 48, 54, 60, 66, 72, 76);
    private static final Set<Integer> SUBMITTED_ADDITION_INDICES =
            Set.of(1, 5, 11, 13, 17, 21, 25, 29, 33, 37, 41, 45, 49, 53, 57, 61, 69, 73);
    private static final Integer[] EXPIRY_DAY_OFFSETS = {-20, 0, 7, 25, 45, 75, 120, 240, null};

    private DemoDocumentSeedCatalog() {
    }

    static List<DocumentSeed> demoDocuments(List<TaskSeed> tasks) {
        List<DocumentSeed> documents = new ArrayList<>(84);
        addExistingDocuments(documents);
        int documentNumber = 8;
        int additionIndex = 0;
        for (int workerNumber = 1; workerNumber <= 28; workerNumber++) {
            for (DocumentType documentType : additionalTypes(workerNumber)) {
                SubmissionStatus status = scenarioStatus(workerNumber, documentType, additionIndex);
                documents.add(document(
                        "95000000-0000-0000-0000-000000000",
                        documentNumber,
                        "92000000-0000-0000-0000-000000000",
                        workerNumber,
                        documentType,
                        status,
                        scenarioExpiryDays(workerNumber, documentType, additionIndex),
                        scenarioDestination(workerNumber, documentType),
                        scenarioNote(workerNumber, documentType, status)
                ));
                documentNumber++;
                additionIndex++;
            }
        }
        return linkToTasks(documents, tasks);
    }

    static List<DocumentSeed> testDocuments(List<TaskSeed> tasks) {
        return linkToTasks(List.of(
                testDocument(1, 1, DocumentType.PASSPORT_COPY, SubmissionStatus.VERIFIED, 120),
                testDocument(2, 1, DocumentType.ARC, SubmissionStatus.MISSING, 15),
                testDocument(3, 2, DocumentType.CONTRACT, SubmissionStatus.SUBMITTED, 45),
                testDocument(4, 2, DocumentType.PERMIT, SubmissionStatus.VERIFIED, 180),
                testDocument(5, 3, DocumentType.PASSPORT_COPY, SubmissionStatus.VERIFIED, null),
                testDocument(6, 3, DocumentType.ARC, SubmissionStatus.SUBMITTED, 7),
                testDocument(7, 4, DocumentType.CONTRACT, SubmissionStatus.MISSING, -10),
                testDocument(8, 5, DocumentType.PERMIT, SubmissionStatus.VERIFIED, 240)
        ), tasks);
    }

    private static List<DocumentSeed> linkToTasks(
            List<DocumentSeed> documents,
            List<TaskSeed> tasks
    ) {
        return documents.stream()
                .map(document -> new DocumentSeed(
                        document.documentId(),
                        document.workerId(),
                        matchingTaskId(document, tasks),
                        document.documentType(),
                        document.submissionStatus(),
                        document.expiryDays(),
                        document.destination(),
                        document.note(),
                        document.fileId()
                ))
                .toList();
    }

    private static UUID matchingTaskId(DocumentSeed document, List<TaskSeed> tasks) {
        TaskType taskType = switch (document.documentType()) {
            case PASSPORT_COPY, ARC -> TaskType.STAY_PERIOD_EXTENSION;
            case CONTRACT -> TaskType.RECONTRACT;
            case PERMIT -> TaskType.EMPLOYMENT_PERIOD_EXTENSION;
        };
        return tasks.stream()
                .filter(task -> task.workerId().equals(document.workerId()))
                .filter(task -> task.taskType() == taskType)
                .map(TaskSeed::taskId)
                .findFirst()
                .orElse(null);
    }

    private static void addExistingDocuments(List<DocumentSeed> documents) {
        documents.add(document(1, 1, DocumentType.PASSPORT_COPY, SubmissionStatus.MISSING, 25,
                "체류기간 연장", "만료 임박 여권 사본을 다시 받아야 합니다."));
        documents.add(document(2, 1, DocumentType.ARC, SubmissionStatus.VERIFIED, 180,
                "체류기간 연장", "외국인등록증 사본 확인 완료"));
        documents.add(document(3, 2, DocumentType.CONTRACT, SubmissionStatus.SUBMITTED, 90,
                "근로계약 갱신", "서명본 검토 대기"));
        documents.add(document(4, 2, DocumentType.PERMIT, SubmissionStatus.VERIFIED, 150,
                "고용허가기간 연장", "고용허가서 확인 완료"));
        documents.add(document(5, 3, DocumentType.PASSPORT_COPY, SubmissionStatus.VERIFIED, 300,
                "체류기간 연장", "유효한 여권 사본"));
        documents.add(document(6, 4, DocumentType.ARC, SubmissionStatus.MISSING, 45,
                "고용허가기간 연장", "외국인등록증 사본 요청 필요"));
        documents.add(document(7, 5, DocumentType.CONTRACT, SubmissionStatus.SUBMITTED, 120,
                "근로계약 갱신", "갱신 계약서 제출 완료"));
    }

    private static List<DocumentType> additionalTypes(int workerNumber) {
        return switch (workerNumber) {
            case 1 -> List.of(DocumentType.CONTRACT);
            case 2 -> List.of(DocumentType.ARC);
            case 3 -> List.of(DocumentType.ARC, DocumentType.CONTRACT);
            case 4 -> List.of(DocumentType.CONTRACT, DocumentType.PERMIT);
            case 5 -> List.of(DocumentType.PASSPORT_COPY, DocumentType.ARC);
            default -> List.of(
                    DocumentType.PASSPORT_COPY,
                    DocumentType.ARC,
                    workerNumber % 4 == 0 ? DocumentType.PERMIT : DocumentType.CONTRACT
            );
        };
    }

    private static SubmissionStatus additionStatus(int additionIndex) {
        if (MISSING_ADDITION_INDICES.contains(additionIndex)) {
            return SubmissionStatus.MISSING;
        }
        if (SUBMITTED_ADDITION_INDICES.contains(additionIndex)) {
            return SubmissionStatus.SUBMITTED;
        }
        return SubmissionStatus.VERIFIED;
    }

    private static SubmissionStatus scenarioStatus(
            int workerNumber,
            DocumentType documentType,
            int additionIndex
    ) {
        if (workerNumber == 6) {
            return documentType == DocumentType.PASSPORT_COPY
                    ? SubmissionStatus.VERIFIED
                    : SubmissionStatus.MISSING;
        }
        return additionStatus(additionIndex);
    }

    private static Integer scenarioExpiryDays(
            int workerNumber,
            DocumentType documentType,
            int additionIndex
    ) {
        if (workerNumber == 6) {
            return switch (documentType) {
                case PASSPORT_COPY -> 365;
                case ARC -> null;
                case CONTRACT -> 180;
                case PERMIT -> throw new IllegalStateException("worker 6 has no permit document seed");
            };
        }
        return EXPIRY_DAY_OFFSETS[additionIndex % EXPIRY_DAY_OFFSETS.length];
    }

    private static String scenarioDestination(int workerNumber, DocumentType documentType) {
        if (workerNumber == 6) {
            return documentType == DocumentType.PASSPORT_COPY
                    ? "체류기간 연장"
                    : "재계약·연장 준비";
        }
        return destination(documentType);
    }

    private static String scenarioNote(
            int workerNumber,
            DocumentType documentType,
            SubmissionStatus status
    ) {
        if (workerNumber == 6) {
            return switch (documentType) {
                case PASSPORT_COPY -> "검증된 유효 여권 사본";
                case ARC -> "외국인등록증 사본 요청 필요";
                case CONTRACT -> "현재 근로계약서 확인 완료";
                case PERMIT -> throw new IllegalStateException("worker 6 has no permit document seed");
            };
        }
        return note(documentType, status);
    }

    private static String destination(DocumentType documentType) {
        return switch (documentType) {
            case PASSPORT_COPY, ARC -> "체류기간 연장";
            case CONTRACT -> "근로계약 갱신";
            case PERMIT -> "고용허가기간 연장";
        };
    }

    private static String note(DocumentType documentType, SubmissionStatus status) {
        String documentName = switch (documentType) {
            case PASSPORT_COPY -> "여권 사본";
            case ARC -> "외국인등록증 사본";
            case CONTRACT -> "근로계약서";
            case PERMIT -> "고용허가서";
        };
        return switch (status) {
            case MISSING -> documentName + " 요청 필요";
            case SUBMITTED -> documentName + " 제출본 검토 대기";
            case VERIFIED -> documentName + " 확인 완료";
        };
    }

    private static DocumentSeed document(
            int documentNumber,
            int workerNumber,
            DocumentType documentType,
            SubmissionStatus status,
            Integer expiryDays,
            String destination,
            String note
    ) {
        return document(
                "95000000-0000-0000-0000-000000000",
                documentNumber,
                "92000000-0000-0000-0000-000000000",
                workerNumber,
                documentType,
                status,
                expiryDays,
                destination,
                note
        );
    }

    private static DocumentSeed testDocument(
            int documentNumber,
            int workerNumber,
            DocumentType documentType,
            SubmissionStatus status,
            Integer expiryDays
    ) {
        return document(
                "98000000-0000-0000-0000-000000000",
                documentNumber,
                "93000000-0000-0000-0000-000000000",
                workerNumber,
                documentType,
                status,
                expiryDays,
                destination(documentType),
                note(documentType, status)
        );
    }

    private static DocumentSeed document(
            String documentPrefix,
            int documentNumber,
            String workerPrefix,
            int workerNumber,
            DocumentType documentType,
            SubmissionStatus status,
            Integer expiryDays,
            String destination,
            String note
    ) {
        return new DocumentSeed(
                demoUuid(documentPrefix, documentNumber),
                demoUuid(workerPrefix, workerNumber),
                null,
                documentType,
                status,
                expiryDays,
                destination,
                note,
                "95000000-0000-0000-0000-000000000".equals(documentPrefix)
                                && documentNumber == 7
                        ? DemoStoredFileSeedCatalog.CONTRACT_FILE_ID
                        : null
        );
    }

    private static UUID demoUuid(String prefix, int number) {
        return UUID.fromString(prefix + "%03d".formatted(number));
    }
}
