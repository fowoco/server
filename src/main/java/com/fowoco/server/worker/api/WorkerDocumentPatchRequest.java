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
        name = "WorkerDocumentPatchRequest",
        description = "근로자 서류 제출·검증·만료 상태와 유효기간을 부분 수정합니다."
)
public final class WorkerDocumentPatchRequest {

    @Schema(description = "서류 유형. 생략 시 변경하지 않습니다.")
    private final DocumentType documentType;

    @Schema(description = "제출 상태. 생략 시 변경하지 않습니다.")
    private final SubmissionStatus submissionStatus;

    @Schema(description = "서류 유효기간. 생략 시 변경하지 않습니다.", format = "date")
    private final LocalDate expiryDate;

    @Schema(description = "제출처. 생략 시 변경하지 않습니다.", maxLength = 120)
    @Size(max = 120, message = "destination은 120자 이하여야 합니다.")
    private final String destination;

    @Schema(description = "메모. 생략 시 변경하지 않습니다.", maxLength = 500)
    @Size(max = 500, message = "note는 500자 이하여야 합니다.")
    private final String note;

    @Schema(
            description = "낙관적 잠금 버전. 마지막으로 조회한 WorkerDocumentResponse.version을 그대로 보내야 합니다.",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "expected_version을 입력해 주세요.")
    private final Long expectedVersion;

    @JsonCreator
    public WorkerDocumentPatchRequest(
            @JsonProperty("document_type") DocumentType documentType,
            @JsonProperty("submission_status") SubmissionStatus submissionStatus,
            @JsonProperty("expiry_date") LocalDate expiryDate,
            @JsonProperty("destination") String destination,
            @JsonProperty("note") String note,
            @JsonProperty("expected_version") Long expectedVersion
    ) {
        this.documentType = documentType;
        this.submissionStatus = submissionStatus;
        this.expiryDate = expiryDate;
        this.destination = destination;
        this.note = note;
        this.expectedVersion = expectedVersion;
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

    public Long getExpectedVersion() {
        return expectedVersion;
    }
}
