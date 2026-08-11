package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "WorkerLinkSmsDeliveryRequest", description = "근로자 보안 링크 SMS 발송 요청")
public final class WorkerLinkSmsDeliveryRequest {

    @NotBlank(message = "recipient_phone을 입력해 주세요.")
    @Size(max = 24, message = "recipient_phone은 24자 이하여야 합니다.")
    @Pattern(
            regexp = "^[+0-9() -]+$",
            message = "recipient_phone 형식을 확인해 주세요."
    )
    @JsonProperty("recipient_phone")
    @Schema(name = "recipient_phone", example = "01012345678")
    private final String recipientPhone;

    @NotBlank(message = "worker_link_token을 입력해 주세요.")
    @Size(min = 32, max = 256, message = "worker_link_token 길이를 확인해 주세요.")
    @Pattern(
            regexp = "^[A-Za-z0-9_-]+$",
            message = "worker_link_token 형식을 확인해 주세요."
    )
    @JsonProperty("worker_link_token")
    @Schema(
            name = "worker_link_token",
            description = "링크 발급 응답의 worker_link_token 값입니다. 발급 직후에만 받을 수 있습니다."
    )
    private final String workerLinkToken;

    @JsonCreator
    public WorkerLinkSmsDeliveryRequest(
            @JsonProperty("recipient_phone") String recipientPhone,
            @JsonProperty("worker_link_token") String workerLinkToken
    ) {
        this.recipientPhone = recipientPhone;
        this.workerLinkToken = workerLinkToken;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getWorkerLinkToken() {
        return workerLinkToken;
    }
}
