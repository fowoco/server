package com.fowoco.server.worker.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(
        name = "WorkerCreateRequest",
        description = "근로자 등록 요청. 여권번호·외국인등록번호·전화번호·계좌번호는 이 API로 수집하지 않습니다."
)
public final class WorkerCreateRequest {

    @Schema(
            name = "display_name",
            description = "화면 표시용 근로자 이름",
            example = "응웬반A",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "표시 이름을 입력해 주세요.")
    @Size(max = 120, message = "표시 이름은 120자 이하여야 합니다.")
    private final String displayName;

    @Schema(
            name = "nationality_code",
            description = "국적 코드",
            example = "VN",
            maxLength = 10
    )
    @Size(max = 10, message = "국적 코드는 10자 이하여야 합니다.")
    private final String nationalityCode;

    @Schema(
            name = "preferred_language",
            description = "선호 언어",
            example = "vi",
            maxLength = 20
    )
    @Size(max = 20, message = "선호 언어는 20자 이하여야 합니다.")
    private final String preferredLanguage;

    @Schema(
            name = "visa_type",
            description = "체류자격 종류",
            example = "E-9",
            maxLength = 20
    )
    @Size(max = 20, message = "체류자격 종류는 20자 이하여야 합니다.")
    private final String visaType;

    @Schema(
            name = "stay_expiry_date",
            description = "체류자격이 만료되는 날",
            example = "2027-03-01",
            format = "date"
    )
    private final LocalDate stayExpiryDate;

    @Schema(
            name = "contract_start_date",
            description = "계약 시작일",
            example = "2026-01-01",
            format = "date"
    )
    private final LocalDate contractStartDate;

    @Schema(
            name = "contract_end_date",
            description = "현재 근로계약이 끝나는 날. contract_start_date보다 빠를 수 없습니다.",
            example = "2027-12-31",
            format = "date"
    )
    private final LocalDate contractEndDate;

    @Schema(
            name = "employment_permit_end_date",
            description = "사업장의 고용허가 관련 기준 종료일",
            example = "2028-03-01",
            format = "date"
    )
    private final LocalDate employmentPermitEndDate;

    @Schema(
            name = "employment_activity_end_date",
            description = "E-9 근로자가 취업활동할 수 있는 기간의 종료일",
            example = "2028-03-01",
            format = "date"
    )
    private final LocalDate employmentActivityEndDate;

    @JsonCreator
    public WorkerCreateRequest(
            @JsonProperty("display_name") String displayName,
            @JsonProperty("nationality_code") String nationalityCode,
            @JsonProperty("preferred_language") String preferredLanguage,
            @JsonProperty("visa_type") String visaType,
            @JsonProperty("stay_expiry_date") LocalDate stayExpiryDate,
            @JsonProperty("contract_start_date") LocalDate contractStartDate,
            @JsonProperty("contract_end_date") LocalDate contractEndDate,
            @JsonProperty("employment_permit_end_date") LocalDate employmentPermitEndDate,
            @JsonProperty("employment_activity_end_date") LocalDate employmentActivityEndDate
    ) {
        this.displayName = displayName;
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.visaType = visaType;
        this.stayExpiryDate = stayExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.employmentPermitEndDate = employmentPermitEndDate;
        this.employmentActivityEndDate = employmentActivityEndDate;
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
}
