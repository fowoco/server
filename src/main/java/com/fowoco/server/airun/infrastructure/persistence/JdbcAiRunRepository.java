package com.fowoco.server.airun.infrastructure.persistence;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiCandidate;
import com.fowoco.server.aiintegration.application.model.AiQuestion;
import com.fowoco.server.aiintegration.application.model.AnalysisInput;
import com.fowoco.server.airun.application.AiRunCandidateResult;
import com.fowoco.server.airun.application.AiRunQuestionResult;
import com.fowoco.server.airun.application.AiRunResult;
import com.fowoco.server.airun.application.error.AiRunErrorCode;
import com.fowoco.server.airun.application.port.AiRunRepository;
import com.fowoco.server.airun.domain.AiRunStatus;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcAiRunRepository implements AiRunRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;

    public JdbcAiRunRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
    }

    @Override
    public Optional<IdempotentRun> findByIdempotencyKeyHash(UUID companyId, String keyHash) {
        return jdbcTemplate.query(
                """
                SELECT ai_run_id, request_id, instruction_hash
                FROM ai_run
                WHERE company_id = ? AND idempotency_key_hash = ?
                """,
                (resultSet, rowNum) -> new IdempotentRun(
                        uuid(resultSet, "ai_run_id"),
                        uuid(resultSet, "request_id"),
                        resultSet.getString("instruction_hash")
                ),
                companyId,
                keyHash
        ).stream().findFirst();
    }

    @Override
    @Transactional
    public void insertPlan(PlanRun run) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_run (
                    ai_run_id, company_id, requested_by, request_id,
                    instruction, instruction_hash, idempotency_key_hash,
                    status, attempt_count, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING', 1, ?, ?, 0)
                """,
                run.aiRunId(),
                run.companyId(),
                run.actorId(),
                run.requestId(),
                run.instruction(),
                run.instructionHash(),
                run.idempotencyKeyHash(),
                timestamp(run.createdAt()),
                timestamp(run.createdAt())
        );
        insertAttempt(new Attempt(
                run.attemptId(),
                run.aiRunId(),
                run.companyId(),
                run.requestId(),
                1,
                com.fowoco.server.aiintegration.application.model.AiAnalysisPhase.PLAN,
                0,
                run.input(),
                run.createdAt()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiRunResult> findByIdAndCompanyId(UUID aiRunId, UUID companyId) {
        Optional<RunRow> run = findRunRow(aiRunId, companyId);
        if (run.isEmpty()) {
            return Optional.empty();
        }
        UUID latestAttemptId = latestAttemptId(aiRunId, companyId).orElse(null);
        List<AiRunQuestionResult> questions = latestAttemptId == null
                ? List.of()
                : findQuestions(latestAttemptId, companyId);
        List<AiRunCandidateResult> candidates = latestAttemptId == null
                ? List.of()
                : findCandidates(latestAttemptId, companyId);
        RunRow row = run.get();
        return Optional.of(new AiRunResult(
                row.aiRunId(),
                row.requestId(),
                row.instruction(),
                row.status(),
                row.outcome(),
                row.detectedIntent(),
                row.errorCode(),
                row.attemptCount(),
                row.version(),
                questions,
                candidates,
                row.createdAt(),
                row.updatedAt()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExecutionState> findExecutionState(UUID aiRunId, UUID companyId) {
        return findExecutionState("run.ai_run_id = ? AND run.company_id = ?", aiRunId, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExecutionState> findExecutionStateByRequestId(UUID requestId) {
        return findExecutionState("run.request_id = ?", requestId);
    }

    @Override
    public void insertAttempt(Attempt attempt) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_attempt (
                    ai_attempt_id, ai_run_id, company_id, request_id,
                    sequence_no, phase, context_round, status,
                    analysis_input_json, started_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?)
                """,
                attempt.attemptId(),
                attempt.aiRunId(),
                attempt.companyId(),
                attempt.requestId(),
                attempt.sequenceNo(),
                attempt.phase().name(),
                attempt.contextRound(),
                encode(attempt.input()),
                timestamp(attempt.startedAt())
        );
    }

    @Override
    @Transactional
    public ExecutionState startContinuationAttempt(
            UUID requestId,
            UUID attemptId,
            com.fowoco.server.aiintegration.application.model.AiAnalysisPhase phase,
            int contextRound,
            AnalysisInput input,
            Instant startedAt
    ) {
        ExecutionState current = findExecutionStateByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("AI Run for request was not found"));
        int nextSequence = current.attemptCount() + 1;
        jdbcTemplate.update(
                """
                UPDATE ai_run
                SET status = 'RUNNING', analysis_outcome = NULL, last_error_code = NULL,
                    attempt_count = ?, updated_at = ?, version = version + 1
                WHERE ai_run_id = ? AND company_id = ? AND version = ?
                """,
                nextSequence,
                timestamp(startedAt),
                current.aiRunId(),
                current.companyId(),
                current.version()
        );
        insertAttempt(new Attempt(
                attemptId,
                current.aiRunId(),
                current.companyId(),
                requestId,
                nextSequence,
                phase,
                contextRound,
                input,
                startedAt
        ));
        return new ExecutionState(
                current.aiRunId(),
                current.companyId(),
                requestId,
                attemptId,
                nextSequence,
                contextRound,
                current.version() + 1,
                input
        );
    }

    @Override
    @Transactional
    public void markAttemptSucceeded(
            UUID aiRunId,
            UUID companyId,
            UUID attemptId,
            AiAnalysisResponse response,
            Instant completedAt
    ) {
        int attempts = jdbcTemplate.update(
                """
                UPDATE ai_attempt
                SET status = 'SUCCEEDED', latency_ms = ?, provider_attempt_count = ?,
                    agent_version = ?, model_provider = ?, model_name = ?, model_version = ?,
                    prompt_version = ?, context_pack_version = ?, workflow_catalog_version = ?,
                    contract_version = ?, knowledge_version = ?, completed_at = ?
                WHERE ai_attempt_id = ? AND ai_run_id = ? AND company_id = ? AND status = 'RUNNING'
                """,
                response.latencyMs(),
                response.providerAttemptCount(),
                response.versions().agentVersion(),
                response.versions().modelProvider(),
                response.versions().modelName(),
                response.versions().modelVersion(),
                response.versions().promptVersion(),
                response.versions().contextPackVersion(),
                response.versions().workflowCatalogVersion(),
                response.versions().contractVersion(),
                response.versions().workflowCatalogVersion(),
                timestamp(completedAt),
                attemptId,
                aiRunId,
                companyId
        );
        if (attempts != 1) {
            throw new IllegalStateException("running AI attempt was not found");
        }
        String detectedIntent = detectedIntent(response);
        jdbcTemplate.update(
                """
                UPDATE ai_run
                SET status = 'SUCCEEDED', analysis_outcome = ?,
                    detected_intent = COALESCE(?, detected_intent),
                    last_error_code = NULL, updated_at = ?, version = version + 1
                WHERE ai_run_id = ? AND company_id = ?
                """,
                response.outcome().name(),
                detectedIntent,
                timestamp(completedAt),
                aiRunId,
                companyId
        );
        insertQuestions(aiRunId, attemptId, companyId, response.questions(), completedAt);
        insertCandidates(aiRunId, attemptId, companyId, response.candidates(), completedAt);
    }

    @Override
    @Transactional
    public void markAttemptFailed(
            UUID aiRunId,
            UUID companyId,
            UUID attemptId,
            String errorCode,
            Instant completedAt
    ) {
        jdbcTemplate.update(
                """
                UPDATE ai_attempt
                SET status = 'FAILED', error_code = ?, completed_at = ?
                WHERE ai_attempt_id = ? AND ai_run_id = ? AND company_id = ? AND status = 'RUNNING'
                """,
                errorCode,
                timestamp(completedAt),
                attemptId,
                aiRunId,
                companyId
        );
        jdbcTemplate.update(
                """
                UPDATE ai_run
                SET status = 'FAILED', analysis_outcome = NULL, last_error_code = ?,
                    updated_at = ?, version = version + 1
                WHERE ai_run_id = ? AND company_id = ?
                """,
                errorCode,
                timestamp(completedAt),
                aiRunId,
                companyId
        );
    }

    @Override
    @Transactional
    public ExecutionState startAnswerAttempt(
            UUID aiRunId,
            UUID companyId,
            UUID actorId,
            long expectedVersion,
            Map<String, String> answers,
            UUID attemptId,
            AnalysisInput input,
            Instant startedAt
    ) {
        RunForUpdate run = jdbcTemplate.query(
                """
                SELECT request_id, status, analysis_outcome, attempt_count, version
                FROM ai_run
                WHERE ai_run_id = ? AND company_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNum) -> new RunForUpdate(
                        uuid(resultSet, "request_id"),
                        AiRunStatus.valueOf(resultSet.getString("status")),
                        nullableOutcome(resultSet.getString("analysis_outcome")),
                        resultSet.getInt("attempt_count"),
                        resultSet.getLong("version")
                ),
                aiRunId,
                companyId
        ).stream().findFirst().orElseThrow(() -> new ApiException(AiRunErrorCode.AI_RUN_NOT_FOUND));
        if (run.version() != expectedVersion) {
            throw new ApiException(AiRunErrorCode.AI_RUN_VERSION_CONFLICT);
        }
        if (run.status() != AiRunStatus.SUCCEEDED
                || run.outcome() != AiAnalysisOutcome.NEEDS_INFO) {
            throw new ApiException(AiRunErrorCode.AI_RUN_ANSWERS_NOT_ALLOWED);
        }
        UUID latestAttemptId = latestAttemptId(aiRunId, companyId)
                .orElseThrow(() -> new IllegalStateException("AI attempt was not found"));
        List<String> allowedKeys = jdbcTemplate.query(
                """
                SELECT slot_key
                FROM ai_question
                WHERE ai_attempt_id = ? AND company_id = ?
                """,
                (resultSet, rowNum) -> resultSet.getString("slot_key"),
                latestAttemptId,
                companyId
        );
        if (answers.isEmpty() || !allowedKeys.containsAll(answers.keySet())) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_ANSWER);
        }
        answers.forEach((slotKey, value) -> jdbcTemplate.update(
                """
                UPDATE ai_question
                SET answer_value = ?, answered_by = ?, answered_at = ?
                WHERE ai_attempt_id = ? AND company_id = ? AND slot_key = ?
                """,
                value,
                actorId,
                timestamp(startedAt),
                latestAttemptId,
                companyId,
                slotKey
        ));
        int nextSequence = run.attemptCount() + 1;
        jdbcTemplate.update(
                """
                UPDATE ai_run
                SET status = 'RUNNING', analysis_outcome = NULL, last_error_code = NULL,
                    attempt_count = ?, updated_at = ?, version = version + 1
                WHERE ai_run_id = ? AND company_id = ? AND version = ?
                """,
                nextSequence,
                timestamp(startedAt),
                aiRunId,
                companyId,
                expectedVersion
        );
        insertAttempt(new Attempt(
                attemptId,
                aiRunId,
                companyId,
                run.requestId(),
                nextSequence,
                com.fowoco.server.aiintegration.application.model.AiAnalysisPhase.ANALYZE,
                0,
                input,
                startedAt
        ));
        return new ExecutionState(
                aiRunId,
                companyId,
                run.requestId(),
                attemptId,
                nextSequence,
                0,
                expectedVersion + 1,
                input
        );
    }

    private Optional<RunRow> findRunRow(UUID aiRunId, UUID companyId) {
        return jdbcTemplate.query(
                """
                SELECT ai_run_id, request_id, instruction, status, analysis_outcome,
                       detected_intent, last_error_code, attempt_count, version,
                       created_at, updated_at
                FROM ai_run
                WHERE ai_run_id = ? AND company_id = ?
                """,
                (resultSet, rowNum) -> new RunRow(
                        uuid(resultSet, "ai_run_id"),
                        uuid(resultSet, "request_id"),
                        resultSet.getString("instruction"),
                        AiRunStatus.valueOf(resultSet.getString("status")),
                        nullableOutcome(resultSet.getString("analysis_outcome")),
                        resultSet.getString("detected_intent"),
                        resultSet.getString("last_error_code"),
                        resultSet.getInt("attempt_count"),
                        resultSet.getLong("version"),
                        instant(resultSet, "created_at"),
                        instant(resultSet, "updated_at")
                ),
                aiRunId,
                companyId
        ).stream().findFirst();
    }

    private Optional<ExecutionState> findExecutionState(String predicate, Object... arguments) {
        String sql = """
                SELECT run.ai_run_id, run.company_id, run.request_id, run.attempt_count, run.version,
                       attempt.ai_attempt_id, attempt.context_round, attempt.analysis_input_json
                FROM ai_run run
                JOIN ai_attempt attempt
                  ON attempt.ai_run_id = run.ai_run_id
                 AND attempt.company_id = run.company_id
                 AND attempt.sequence_no = run.attempt_count
                WHERE %s
                """.formatted(predicate);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new ExecutionState(
                        uuid(resultSet, "ai_run_id"),
                        uuid(resultSet, "company_id"),
                        uuid(resultSet, "request_id"),
                        uuid(resultSet, "ai_attempt_id"),
                        resultSet.getInt("attempt_count"),
                        resultSet.getInt("context_round"),
                        resultSet.getLong("version"),
                        decodeInput(resultSet.getString("analysis_input_json"))
                ),
                arguments
        ).stream().findFirst();
    }

    private Optional<UUID> latestAttemptId(UUID aiRunId, UUID companyId) {
        return jdbcTemplate.query(
                """
                SELECT ai_attempt_id
                FROM ai_attempt
                WHERE ai_run_id = ? AND company_id = ?
                ORDER BY sequence_no DESC
                FETCH FIRST 1 ROW ONLY
                """,
                (resultSet, rowNum) -> uuid(resultSet, "ai_attempt_id"),
                aiRunId,
                companyId
        ).stream().findFirst();
    }

    private List<AiRunQuestionResult> findQuestions(UUID attemptId, UUID companyId) {
        return jdbcTemplate.query(
                """
                SELECT slot_key, label, input_type, required, answer_value
                FROM ai_question
                WHERE ai_attempt_id = ? AND company_id = ?
                ORDER BY created_at, slot_key
                """,
                (resultSet, rowNum) -> new AiRunQuestionResult(
                        resultSet.getString("slot_key"),
                        resultSet.getString("label"),
                        resultSet.getString("input_type"),
                        resultSet.getBoolean("required"),
                        resultSet.getString("answer_value")
                ),
                attemptId,
                companyId
        );
    }

    private List<AiRunCandidateResult> findCandidates(UUID attemptId, UUID companyId) {
        return jdbcTemplate.query(
                """
                SELECT ai_candidate_id, candidate_ref, worker_id, workflow_id,
                       extracted_slots_json, missing_slots_json, confidence
                FROM ai_candidate
                WHERE ai_attempt_id = ? AND company_id = ?
                ORDER BY created_at, candidate_ref
                """,
                (resultSet, rowNum) -> new AiRunCandidateResult(
                        uuid(resultSet, "ai_candidate_id"),
                        resultSet.getString("candidate_ref"),
                        uuid(resultSet, "worker_id"),
                        resultSet.getString("workflow_id"),
                        decodeStringMap(resultSet.getString("extracted_slots_json")),
                        decodeStringList(resultSet.getString("missing_slots_json")),
                        resultSet.getBigDecimal("confidence")
                ),
                attemptId,
                companyId
        );
    }

    private void insertQuestions(
            UUID aiRunId,
            UUID attemptId,
            UUID companyId,
            List<AiQuestion> questions,
            Instant createdAt
    ) {
        questions.forEach(question -> jdbcTemplate.update(
                """
                INSERT INTO ai_question (
                    ai_question_id, ai_run_id, ai_attempt_id, company_id,
                    slot_key, label, input_type, required, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'TEXT', TRUE, ?)
                """,
                uuidGenerator.generate(),
                aiRunId,
                attemptId,
                companyId,
                question.slotKey(),
                question.prompt(),
                timestamp(createdAt)
        ));
    }

    private void insertCandidates(
            UUID aiRunId,
            UUID attemptId,
            UUID companyId,
            List<AiCandidate> candidates,
            Instant createdAt
    ) {
        candidates.forEach(candidate -> jdbcTemplate.update(
                """
                INSERT INTO ai_candidate (
                    ai_candidate_id, ai_run_id, ai_attempt_id, company_id,
                    candidate_ref, worker_id, workflow_id, extracted_slots_json,
                    missing_slots_json, confidence, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                uuidGenerator.generate(),
                aiRunId,
                attemptId,
                companyId,
                candidate.candidateRef(),
                candidate.workerRef(),
                candidate.workflowId(),
                encode(candidate.extractedSlots()),
                encode(candidate.missingSlots()),
                candidate.confidence(),
                timestamp(createdAt)
        ));
    }

    private String detectedIntent(AiAnalysisResponse response) {
        if (response.contextRequirement() != null) {
            return response.contextRequirement().detectedIntent();
        }
        // ANALYZE candidates carry canonical Workflow IDs, not Intent codes.
        // Returning null preserves the Intent detected during PLAN through the COALESCE update.
        return null;
    }

    private String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("AI Run data cannot be encoded", exception);
        }
    }

    private AnalysisInput decodeInput(String json) {
        try {
            return objectMapper.readValue(json, AnalysisInput.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored AI analysis input cannot be decoded", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> decodeStringMap(String json) {
        try {
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(key, value == null ? null : value.toString()));
            return result;
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored candidate slots cannot be decoded", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> decodeStringList(String json) {
        try {
            List<Object> raw = objectMapper.readValue(json, List.class);
            List<String> result = new ArrayList<>();
            raw.forEach(value -> result.add(value.toString()));
            return result;
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored candidate missing slots cannot be decoded", exception);
        }
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private AiAnalysisOutcome nullableOutcome(String value) {
        return value == null ? null : AiAnalysisOutcome.valueOf(value);
    }

    private record RunRow(
            UUID aiRunId,
            UUID requestId,
            String instruction,
            AiRunStatus status,
            AiAnalysisOutcome outcome,
            String detectedIntent,
            String errorCode,
            int attemptCount,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    private record RunForUpdate(
            UUID requestId,
            AiRunStatus status,
            AiAnalysisOutcome outcome,
            int attemptCount,
            long version
    ) {
    }
}
