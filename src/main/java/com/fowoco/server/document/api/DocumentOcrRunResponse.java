package com.fowoco.server.document.api;

import com.fowoco.server.document.application.DocumentOcrResultPayload;
import com.fowoco.server.document.application.DocumentOcrRunResult;
import com.fowoco.server.document.domain.DocumentOcrRun;
import com.fowoco.server.document.domain.DocumentOcrRunStatus;
import com.fowoco.server.worker.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "문서 OCR 실행 상태와 HR 검토용 추출 결과")
public record DocumentOcrRunResponse(
        UUID ocrRunId,
        UUID documentId,
        UUID fileId,
        DocumentType documentType,
        DocumentOcrRunStatus status,
        DocumentOcrResultPayload result,
        @Schema(description = "HR 수정값. OCR 원본과 분리 저장되며 Worker·Document에는 자동 반영되지 않습니다.")
        Map<String, String> correctedFields,
        String errorCode,
        UUID reviewedBy,
        String reviewReason,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant reviewedAt,
        Instant updatedAt,
        long version,
        boolean alreadyRequested
) {
    public static DocumentOcrRunResponse from(DocumentOcrRunResult result) {
        DocumentOcrRun run = result.run();
        return new DocumentOcrRunResponse(
                run.ocrRunId(),
                run.workerDocumentId(),
                run.storedFileId(),
                run.documentType(),
                run.status(),
                result.result(),
                result.correctedFields(),
                run.lastErrorCode(),
                run.reviewedBy(),
                run.reviewReason(),
                run.createdAt(),
                run.startedAt(),
                run.completedAt(),
                run.reviewedAt(),
                run.updatedAt(),
                run.version(),
                result.alreadyRequested()
        );
    }
}
