package com.fowoco.server.notification.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.notification.application.NotificationPageResult;
import com.fowoco.server.notification.application.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 조회·읽음 처리")
@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class NotificationController {

    private final NotificationService notificationService;
    private final ActorContextProvider actorContextProvider;

    public NotificationController(
            NotificationService notificationService,
            ActorContextProvider actorContextProvider
    ) {
        this.notificationService = notificationService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "listNotifications",
            summary = "알림 목록 조회",
            description = "상단 알림 패널에 승인·응답·기한·서류 알림과 이동 대상을 제공합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = NotificationPageResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public NotificationPageResponse list(
            @Parameter(description = "읽지 않은 알림만 조회") @RequestParam(required = false) Boolean unreadOnly,
            @Parameter(description = "이전 페이지 마지막 항목의 occurred_at (다음 페이지 조회용)")
            @RequestParam(required = false) Instant cursor,
            @Parameter(description = "페이지당 항목 수 (1~100)")
            @RequestParam(required = false) @Min(1) @Max(100) Integer size
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        NotificationPageResult result = notificationService.findPage(actor, unreadOnly, cursor, size);
        return new NotificationPageResponse(
                result.items().stream().map(NotificationItemResponse::from).toList(),
                result.unreadCount(),
                result.nextCursor()
        );
    }

    @Operation(
            operationId = "readNotification",
            summary = "알림 읽음 처리",
            description = "사용자가 확인한 알림을 읽음으로 기록합니다. 같은 요청을 반복해도 결과는 동일합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "처리 성공"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @PostMapping(path = "/{notificationId}/read")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public ResponseEntity<Void> read(
            @Parameter(description = "알림 ID") @PathVariable UUID notificationId
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        notificationService.markAsRead(notificationId, actor);
        return ResponseEntity.noContent().build();
    }
}
