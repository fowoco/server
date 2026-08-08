package com.fowoco.server.document.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.document.domain.DocumentOcrReviewDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record DocumentOcrReviewRequest(
        @JsonProperty("expected_version")
        @NotNull(message = "expected_version은 필수입니다.")
        @Min(value = 0, message = "expected_version은 0 이상이어야 합니다.")
        @Schema(description = "화면에서 확인한 OCR 실행 version", example = "2")
        Long expectedVersion,

        @NotNull(message = "decision은 필수입니다.")
        @Schema(description = "검토 결정. APPROVE는 업무 데이터 반영이 아닌 OCR 검토 완료를 뜻합니다.", example = "APPROVE")
        DocumentOcrReviewDecision decision,

        @Size(max = 300, message = "검토 사유는 300자 이하여야 합니다.")
        @Schema(description = "반려 시 필수인 검토 사유", example = "여권번호를 원본과 다시 확인해야 합니다.")
        String reason,

        @JsonProperty("corrected_fields")
        @Size(max = 20, message = "수정 필드는 20개 이하여야 합니다.")
        @Schema(
                description = "HR이 원문과 대조해 바로잡은 필드. OCR 원본과 분리해 암호화 저장하며 Worker에는 자동 반영하지 않습니다.",
                example = "{\"passport_number\":\"M12345678\"}"
        )
        Map<String, String> correctedFields
) {
}
