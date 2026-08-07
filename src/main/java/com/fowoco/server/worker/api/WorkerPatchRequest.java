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
            name = "display_name",
            description = "화면 표시용 근로자 이름. 생략 시 변경하지 않습니다.",
            example = "응우옌반A",
            maxLength = 120
    )
    @Size(max = 120, message = "표시 이름은 120자 이하여야 합니다.")
    private final String displayName;

    @Schema(
            name = "nationality_code",
            description = "국적 코드. 생략 시 변경하지 않습니다.",
            example = "VN",
            maxLength = 10
    )
    @Size(max = 10, message = "국적 코드는 10자 이하여야 합니다.")
    private final String nationalityCode;

    @Schema(
            name = "preferred_language",
            description = "선호 언어. 생략 시 변경하지 않습니다.",
            example = "vi",
            maxLength = 20
    )
    @Size(max = 20, message = "선호 언어는 20자 이하여야 합니다.")
    private final String preferredLanguage;

    @Schema(
            name = "work_status",
            description = "근무 상태. 생략 시 변경하지 않습니다."
    )
    private final WorkerStatus workStatus;

    @Schema(
            name = "visa_type",
            description = "체류자격 종류. 생략 시 변경하지 않습니다.",
            example = "E-9",
            maxLength = 20
    )
    @Size(max = 20, message = "체류자격 종류는 20자 이하여야 합니다.")
    private final String visaType;

    @Schema(
            name = "stay_expiry_date",
            description = "체류자격이 만료되는 날. 생략 시 변경하지 않습니다.",
            example = "2027-03-01",
            format = "date"
    )
    private final LocalDate stayExpiryDate;

    @Schema(
            name = "contract_start_date",
            description = "계약 시작일. 생략 시 변경하지 않습니다.",
            example = "2026-01-01",
            format = "date"
    )
    private final LocalDate contractStartDate;

    @Schema(
            name = "contract_end_date",
            description = "현재 근로계약이 끝나는 날. 생략 시 변경하지 않습니다.",
            example = "2027-12-31",
            format = "date"
    )
    private final LocalDate contractEndDate;

    @Schema(
            name = "employment_permit_end_date",
            description = "사업장의 고용허가 관련 기준 종료일. 생략 시 변경하지 않습니다.",
            example = "2028-03-01",
            format = "date"
    )
    private final LocalDate employmentPermitEndDate;

    @Schema(
            name = "employment_activity_end_date",
            description = "E-9 근로자가 취업활동할 수 있는 기간의 종료일. 생략 시 변경하지 않습니다.",
            example = "2028-03-01",
            format = "date"
    )
    private final LocalDate employmentActivityEndDate;

    @Schema(
            name = "expected_version",
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
            @JsonProperty("visa_type") String visaType,
            @JsonProperty("stay_expiry_date") LocalDate stayExpiryDate,
            @JsonProperty("contract_start_date") LocalDate contractStartDate,
            @JsonProperty("contract_end_date") LocalDate contractEndDate,
            @JsonProperty("employment_permit_end_date") LocalDate employmentPermitEndDate,
            @JsonProperty("employment_activity_end_date") LocalDate employmentActivityEndDate,
            @JsonProperty("expected_version") Long expectedVersion
    ) {
        this.displayName = displayName;
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.workStatus = workStatus;
        this.visaType = visaType;
        this.stayExpiryDate = stayExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.employmentPermitEndDate = employmentPermitEndDate;
        this.employmentActivityEndDate = employmentActivityEndDate;
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

    public String getVisaType() {
        return visaType;
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

    public LocalDate getEmploymentPermitEndDate() {
        return employmentPermitEndDate;
    }

    public LocalDate getEmploymentActivityEndDate() {
        return employmentActivityEndDate;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }
}
