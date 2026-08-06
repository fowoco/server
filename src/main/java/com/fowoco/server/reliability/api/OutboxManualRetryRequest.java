package com.fowoco.server.reliability.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OutboxManualRetryRequest(
        @JsonProperty("expected_version")
        @Schema(description = "운영자가 확인한 현재 Outbox event version", example = "3")
        @Min(value = 0, message = "expected_version은 0 이상이어야 합니다.")
        long expectedVersion,

        @Schema(
                description = "재처리 근거. 개인정보·payload·token·예외 원문은 입력하지 않습니다.",
                example = "일시 중단된 내부 handler 복구를 확인함"
        )
        @NotBlank(message = "재처리 사유를 입력해 주세요.")
        @Size(min = 10, max = 300, message = "재처리 사유는 10자 이상 300자 이하로 입력해 주세요.")
        String reason
) {
}
