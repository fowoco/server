package com.fowoco.server.worker.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(
        name = "WorkerDocumentCreateRequest",
        description = "근로자 서류 상태 등록 요청. MVP는 메타데이터 중심이며 파일 실체는 file_id로만 연결"
)
public final class WorkerDocumentCreateRequest {

    @Schema(
            name = "document_type",
            description = "서류 유형",
            allowableValues = {"PASSPORT_COPY", "ARC", "CONTRACT", "PERMIT"},
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "document_type을 입력해 주세요.")
    private final DocumentType documentType;

    @Schema(
            name = "submission_status",
            description = "제출 상태",
            allowableValues = {"MISSING", "SUBMITTED", "VERIFIED"},
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "submission_status를 입력해 주세요.")
    private final SubmissionStatus submissionStatus;

    @Schema(name = "expiry_date", description = "서류 유효기간", example = "2027-03-01", format = "date")
    private final LocalDate expiryDate;

    @Schema(description = "제출처", example = "출입국관리사무소", maxLength = 120)
    @Size(max = 120, message = "destination은 120자 이하여야 합니다.")
    private final String destination;

    @Schema(description = "메모", maxLength = 500)
    @Size(max = 500, message = "note는 500자 이하여야 합니다.")
    private final String note;

    @JsonCreator
    public WorkerDocumentCreateRequest(
            @JsonProperty("document_type") DocumentType documentType,
            @JsonProperty("submission_status") SubmissionStatus submissionStatus,
            @JsonProperty("expiry_date") LocalDate expiryDate,
            @JsonProperty("destination") String destination,
            @JsonProperty("note") String note
    ) {
        this.documentType = documentType;
        this.submissionStatus = submissionStatus;
        this.expiryDate = expiryDate;
        this.destination = destination;
        this.note = note;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public SubmissionStatus getSubmissionStatus() {
        return submissionStatus;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getDestination() {
        return destination;
    }

    public String getNote() {
        return note;
    }
}
