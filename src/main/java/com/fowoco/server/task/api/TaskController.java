package com.fowoco.server.task.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.task.application.TaskResult;
import com.fowoco.server.task.application.TaskWorkflowService;
import com.fowoco.server.task.application.action.TaskAvailableActionResolver;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskTargetType;
import com.fowoco.server.task.domain.TaskType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Task", description = "업무카드·체크리스트·상태 전이")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskWorkflowService taskService;
    private final ActorContextProvider actorContextProvider;
    private final TaskAvailableActionResolver actionResolver;

    public TaskController(
            TaskWorkflowService taskService,
            ActorContextProvider actorContextProvider,
            TaskAvailableActionResolver actionResolver
    ) {
        this.taskService = taskService;
        this.actorContextProvider = actorContextProvider;
        this.actionResolver = actionResolver;
    }

    @Operation(operationId = "listTasks", summary = "업무카드 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사업장 범위 업무카드 목록"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public TaskPageResponse findAll(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskType taskType,
            @RequestParam(name = "target_type", required = false) TaskTargetType targetType,
            @RequestParam(required = false) TaskSource source,
            @RequestParam(required = false) UUID workerId,
            @Parameter(description = "Case ID 필터")
            @RequestParam(name = "case_id", required = false) UUID caseId,
            @RequestParam(required = false) LocalDate dueFrom,
            @RequestParam(required = false) LocalDate dueTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return TaskPageResponse.from(taskService.findAll(
                status,
                taskType,
                targetType,
                source,
                workerId,
                caseId,
                dueFrom,
                dueTo,
                keyword,
                page,
                size,
                actor()
        ));
    }

    @Operation(operationId = "createTask", summary = "수동 업무카드 생성")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "업무카드 생성"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TaskDetailResponse> create(
            @Valid @RequestBody CreateTaskRequest request,
            HttpServletRequest servletRequest
    ) {
        TaskResult result = taskService.create(
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{taskId}")
                .buildAndExpand(result.task().taskId())
                .toUri();
        return ResponseEntity.created(location).body(response(result));
    }

    @Operation(operationId = "getTask", summary = "업무카드 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업무카드와 체크리스트"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    @GetMapping(path = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TaskDetailResponse findById(@PathVariable UUID taskId) {
        return response(taskService.findById(taskId, actor()));
    }

    @Operation(
            operationId = "updateTask",
            summary = "업무카드 내용 수정",
            description = "상태를 직접 받지 않으며 중요값 변경 시 기존 승인을 무효화합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정된 업무카드"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PatchMapping(
            path = "/{taskId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public TaskDetailResponse update(
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            HttpServletRequest servletRequest
    ) {
        return response(taskService.update(
                taskId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    @Operation(
            operationId = "changeTaskAssignee",
            summary = "업무카드 담당자 변경",
            description = "같은 사업장의 활성 HR 또는 관리자를 담당자로 지정합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "담당자가 변경된 업무카드"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PatchMapping(
            path = "/{taskId}/assignee",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public TaskDetailResponse changeAssignee(
            @PathVariable UUID taskId,
            @Valid @RequestBody ChangeTaskAssigneeRequest request,
            HttpServletRequest servletRequest
    ) {
        return response(taskService.changeAssignee(
                taskId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    @Operation(operationId = "updateTaskChecklistItem", summary = "체크리스트 항목 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "체크리스트와 재평가된 업무 상태"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PatchMapping(
            path = "/{taskId}/checklist-items/{itemId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public TaskDetailResponse updateChecklistItem(
            @PathVariable UUID taskId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateChecklistItemRequest request,
            HttpServletRequest servletRequest
    ) {
        return response(taskService.updateChecklistItem(
                taskId,
                itemId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    @Operation(operationId = "cancelTask", summary = "업무카드 취소")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소된 업무카드"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PostMapping(
            path = "/{taskId}/cancel",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public TaskDetailResponse cancel(
            @PathVariable UUID taskId,
            @Valid @RequestBody CancelTaskRequest request,
            HttpServletRequest servletRequest
    ) {
        return response(taskService.cancel(
                taskId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    private ActorContext actor() {
        return actorContextProvider.requireCurrentActor();
    }

    private TaskDetailResponse response(TaskResult result) {
        return TaskDetailResponse.from(result, actionResolver.resolve(result));
    }
}
