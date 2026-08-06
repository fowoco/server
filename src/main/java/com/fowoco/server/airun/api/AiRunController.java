package com.fowoco.server.airun.api;

import com.fowoco.server.airun.application.AiCandidateDecisionCommand;
import com.fowoco.server.airun.application.AiCandidateDecisionService;
import com.fowoco.server.airun.application.AiRunService;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "AI Run", description = "자연어 업무 분석 실행·질문·답변")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/ai-runs")
public class AiRunController {

    private final AiRunService aiRunService;
    private final AiCandidateDecisionService candidateDecisionService;
    private final ActorContextProvider actorContextProvider;

    public AiRunController(
            AiRunService aiRunService,
            AiCandidateDecisionService candidateDecisionService,
            ActorContextProvider actorContextProvider
    ) {
        this.aiRunService = aiRunService;
        this.candidateDecisionService = candidateDecisionService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "createAiRun",
            summary = "AI 업무 분석 요청",
            description = "발화문 하나를 저장한 뒤 AI Runtime 분석을 시작합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "분석 요청 접수"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AiRunResponse> create(
            @Parameter(description = "같은 화면 요청의 중복 생성을 막는 키", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateAiRunRequest request,
            HttpServletRequest servletRequest
    ) {
        AiRunResponse response = AiRunResponse.from(aiRunService.createAndExecute(
                request.instruction(),
                idempotencyKey,
                actor(),
                RequestMetadata.from(servletRequest)
        ));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{aiRunId}")
                .buildAndExpand(response.aiRunId())
                .toUri();
        return ResponseEntity.accepted().location(location).body(response);
    }

    @Operation(operationId = "getAiRun", summary = "AI 분석 상태·질문 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 실행·분석 상태"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    @GetMapping(path = "/{aiRunId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AiRunResponse findById(@PathVariable UUID aiRunId) {
        return AiRunResponse.from(aiRunService.requireRun(aiRunId, actor()));
    }

    @Operation(operationId = "answerAiRunQuestions", summary = "누락 Slot 답변 제출")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "답변 저장 및 새 분석 시도"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PostMapping(
            path = "/{aiRunId}/answers",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AiRunResponse> answer(
            @PathVariable UUID aiRunId,
            @Valid @RequestBody SubmitAiRunAnswersRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.accepted().body(AiRunResponse.from(aiRunService.answerAndExecute(
                aiRunId,
                request.expectedVersion(),
                request.answers(),
                actor(),
                RequestMetadata.from(servletRequest)
        )));
    }

    @Operation(
            operationId = "decideAiRunCandidates",
            summary = "AI 업무 후보 채택·폐기",
            description = "HR이 채택한 후보만 Case와 업무카드로 생성합니다. 승인과 발송은 별도입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후보 결정 완료"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PostMapping(
            path = "/{aiRunId}/candidate-decisions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public AiCandidateDecisionResponse decideCandidates(
            @PathVariable UUID aiRunId,
            @Parameter(description = "같은 후보 결정을 중복 생성하지 않기 위한 키", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DecideAiRunCandidatesRequest request,
            HttpServletRequest servletRequest
    ) {
        AiCandidateDecisionCommand command = new AiCandidateDecisionCommand(
                request.expectedRunVersion(),
                request.decisions().stream()
                        .map(decision -> new AiCandidateDecisionCommand.Decision(
                                decision.candidateId(),
                                decision.action()
                        ))
                        .toList()
        );
        return AiCandidateDecisionResponse.from(candidateDecisionService.decide(
                aiRunId,
                idempotencyKey,
                command,
                actor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    private ActorContext actor() {
        return actorContextProvider.requireCurrentActor();
    }
}
