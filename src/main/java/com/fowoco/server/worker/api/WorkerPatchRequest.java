package com.fowoco.server.worker.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.WorkerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(
        name = "WorkerPatchRequest",
        description = "근로자 부분 수정 요청. 보낸 필드만 갱신되며, 생략한 필드는 값이 없어도 변경하지 않습니다."
)
public final class WorkerPatchRequest {

    @Schema(
            description = "화면 표시용 근로자 이름. 생략 시 변경하지 않습니다.",
            example = "응우옌반A",
            maxLength = 120
    )
    @Size(max = 120, message = "표시 이름은 120자 이하여야 합니다.")
    private final String displayName;

    @Schema(
            description = "국적 코드. 생략 시 변경하지 않습니다.",
            example = "VN",
            maxLength = 10
    )
    @Size(max = 10, message = "국적 코드는 10자 이하여야 합니다.")
    private final String nationalityCode;

    @Schema(
            description = "선호 언어. 생략 시 변경하지 않습니다.",
            example = "vi",
            maxLength = 20
    )
    @Size(max = 20, message = "선호 언어는 20자 이하여야 합니다.")
    private final String preferredLanguage;

    @Schema(
            description = "근무 상태. 생략 시 변경하지 않습니다."
    )
    private final WorkerStatus workStatus;

    @Schema(
            description = "체류 만료일. 생략 시 변경하지 않습니다.",
            example = "2027-03-01",
            format = "date"
    )
    private final LocalDate stayExpiryDate;

    @Schema(
            description = "계약 시작일. 생략 시 변경하지 않습니다.",
            example = "2026-01-01",
            format = "date"
    )
    private final LocalDate contractStartDate;

    @Schema(
            description = "계약 종료일. 생략 시 변경하지 않습니다.",
            example = "2027-12-31",
            format = "date"
    )
    private final LocalDate contractEndDate;

    @Schema(
            description = "낙관적 잠금 버전. 마지막으로 조회한 WorkerResponse.version을 그대로 보내야 합니다.",
            example = "0",
            minimum = "0",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "expected_version을 입력해 주세요.")
    private final Long expectedVersion;

    @JsonCreator
    public WorkerPatchRequest(
            @JsonProperty("display_name") String displayName,
            @JsonProperty("nationality_code") String nationalityCode,
            @JsonProperty("preferred_language") String preferredLanguage,
            @JsonProperty("work_status") WorkerStatus workStatus,
            @JsonProperty("stay_expiry_date") LocalDate stayExpiryDate,
            @JsonProperty("contract_start_date") LocalDate contractStartDate,
            @JsonProperty("contract_end_date") LocalDate contractEndDate,
            @JsonProperty("expected_version") Long expectedVersion
    ) {
        this.displayName = displayName;
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.workStatus = workStatus;
        this.stayExpiryDate = stayExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.expectedVersion = expectedVersion;
    }

    @AssertTrue(message = "display_name을 보낼 경우 공백일 수 없습니다.")
    @Schema(hidden = true)
    public boolean isDisplayNameValid() {
        return displayName == null || !displayName.isBlank();
    }

    @AssertTrue(message = "contract_end_date는 contract_start_date보다 빠를 수 없습니다.")
    @Schema(hidden = true)
    public boolean isContractPeriodValid() {
        if (contractStartDate == null || contractEndDate == null) {
            return true;
        }
        return !contractEndDate.isBefore(contractStartDate);
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

    public LocalDate getStayExpiryDate() {
        return stayExpiryDate;
    }

    public LocalDate getContractStartDate() {
        return contractStartDate;
    }

    public LocalDate getContractEndDate() {
        return contractEndDate;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }
}
