package com.fowoco.server.worker.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(
        name = "WorkerResponse",
        description = "근로자 상세 응답. legal_name, phone 등 worker_sensitive_data 필드는 포함하지 않습니다."
)
public final class WorkerResponse {

    @JsonProperty("worker_id")
    @Schema(
            name = "worker_id",
            description = "근로자 ID",
            format = "uuid",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final UUID workerId;
    @JsonProperty("company_id")
    @Schema(
            name = "company_id",
            description = "소속 사업장 ID",
            format = "uuid",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final UUID companyId;
    @JsonProperty("display_name")
    @Schema(
            name = "display_name",
            description = "화면 표시용 근로자 이름",
            example = "응웬반A",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String displayName;
    @JsonProperty("nationality_code")
    @Schema(
            name = "nationality_code",
            description = "국적 코드",
            example = "VN"
    )
    private final String nationalityCode;
    @JsonProperty("preferred_language")
    @Schema(
            name = "preferred_language",
            description = "선호 언어",
            example = "vi"
    )
    private final String preferredLanguage;
    @JsonProperty("work_status")
    @Schema(
            name = "work_status",
            description = "근무 상태",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final WorkerStatus workStatus;
    @JsonProperty("visa_expiry_date")
    @Schema(
            name = "visa_expiry_date",
            description = "체류 만료일",
            format = "date"
    )
    private final LocalDate visaExpiryDate;
    @JsonProperty("contract_start_date")
    @Schema(
            name = "contract_start_date",
            description = "계약 시작일",
            format = "date"
    )
    private final LocalDate contractStartDate;
    @JsonProperty("contract_end_date")
    @Schema(
            name = "contract_end_date",
            description = "계약 종료일",
            format = "date"
    )
    private final LocalDate contractEndDate;
    @JsonProperty("created_at")
    @Schema(
            name = "created_at",
            description = "근로자 등록 시각(UTC)",
            format = "date-time",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final Instant createdAt;
    @JsonProperty("updated_at")
    @Schema(
            name = "updated_at",
            description = "마지막 수정 시각(UTC)",
            format = "date-time",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final Instant updatedAt;
    @JsonProperty("version")
    @Schema(
            description = "낙관적 잠금 버전. PATCH 요청 시 expected_version으로 그대로 보내야 합니다.",
            example = "0",
            minimum = "0",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final long version;

    private WorkerResponse(
            UUID workerId,
            UUID companyId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            WorkerStatus workStatus,
            LocalDate visaExpiryDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.workerId = workerId;
        this.companyId = companyId;
        this.displayName = displayName;
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.workStatus = workStatus;
        this.visaExpiryDate = visaExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static WorkerResponse from(Worker worker) {
        return new WorkerResponse(
                worker.workerId(),
                worker.companyId(),
                worker.displayName(),
                worker.nationalityCode(),
                worker.preferredLanguage(),
                worker.workStatus(),
                worker.visaExpiryDate(),
                worker.contractStartDate(),
                worker.contractEndDate(),
                worker.createdAt(),
                worker.updatedAt(),
                worker.version()
        );
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNationalityCode() {
        return nationalityCode;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public WorkerStatus getWorkStatus() {
        return workStatus;
    }

    public LocalDate getVisaExpiryDate() {
        return visaExpiryDate;
    }

    public LocalDate getContractStartDate() {
        return contractStartDate;
    }

    public LocalDate getContractEndDate() {
        return contractEndDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
