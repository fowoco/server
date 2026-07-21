package com.fowoco.server.health.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서버 실행 상태 확인")
@RestController
public class HealthController {

    @Operation(
            summary = "서버 상태 확인",
            description = "인증 없이 서버가 요청을 받을 수 있는지 확인합니다.",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "정상",
                    content = @Content(
                            mediaType = MediaType.TEXT_PLAIN_VALUE,
                            schema = @Schema(type = "string", example = "OK")
                    )
            )
    )
    @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public String health() {
        return "OK";
    }
}
