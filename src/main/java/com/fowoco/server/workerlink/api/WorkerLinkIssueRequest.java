package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.settings.domain.CompanySettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(name = "WorkerLinkIssueRequest", description = "근로자 보안 링크 발급 요청")
public final class WorkerLinkIssueRequest {

    @Min(CompanySettings.MIN_LINK_EXPIRY_HOURS)
    @Max(CompanySettings.MAX_LINK_EXPIRY_HOURS)
    @Schema(
            name = "expires_in_hours",
            description = "링크 유효 시간(시간 단위). 생략하면 회사 설정값을 사용합니다.",
            minimum = "1",
            maximum = "168",
            example = "72"
    )
    private final Long expiresInHours;

    @Schema(name = "rotate_existing", description = "기존 활성 링크를 폐기하고 재발급할지 여부")
    private final boolean rotateExisting;

    @JsonCreator
    public WorkerLinkIssueRequest(
            @JsonProperty("expires_in_hours") Long expiresInHours,
            @JsonProperty("rotate_existing") Boolean rotateExisting
    ) {
        this.expiresInHours = expiresInHours;
        this.rotateExisting = rotateExisting != null && rotateExisting;
    }

    public Long getExpiresInHours() {
        return expiresInHours;
    }

    public boolean isRotateExisting() {
        return rotateExisting;
    }
}
