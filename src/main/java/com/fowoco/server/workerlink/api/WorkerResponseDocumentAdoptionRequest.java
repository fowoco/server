package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
        name = "WorkerResponseDocumentAdoptionRequest",
        description = "근로자가 제출한 파일을 공식 근로자 서류로 채택하는 요청"
)
public final class WorkerResponseDocumentAdoptionRequest {

    @NotNull(message = "expected_task_version을 입력해 주세요.")
    @Schema(
            name = "expected_task_version",
            description = "마지막으로 조회한 Task version",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final Long expectedTaskVersion;

    @JsonCreator
    public WorkerResponseDocumentAdoptionRequest(
            @JsonProperty("expected_task_version") Long expectedTaskVersion
    ) {
        this.expectedTaskVersion = expectedTaskVersion;
    }

    public long getExpectedTaskVersion() {
        return expectedTaskVersion;
    }
}
