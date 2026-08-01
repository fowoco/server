package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WorkerLinkIssueRequest", description = "근로자 보안 링크 발급 요청")
public final class WorkerLinkIssueRequest {

    @Schema(name = "expires_in_hours", description = "링크 유효 시간(시간 단위)", example = "72")
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
