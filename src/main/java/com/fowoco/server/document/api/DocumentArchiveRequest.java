package com.fowoco.server.document.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentArchiveRequest(
        @JsonProperty("expected_version")
        @NotNull(message = "expected_version은 필수입니다.")
        @Min(value = 0, message = "expected_version은 0 이상이어야 합니다.")
        @Schema(description = "마지막으로 조회한 문서 version", example = "1")
        Long expectedVersion,

        @NotBlank(message = "보관 사유를 입력해 주세요.")
        @Size(max = 300, message = "보관 사유는 300자 이하여야 합니다.")
        @Schema(description = "문서를 일반 문서함에서 숨기는 이유", example = "잘못 등록한 중복 서류")
        String reason
) {
}
