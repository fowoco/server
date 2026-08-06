package com.fowoco.server.airun.infrastructure.persistence;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.airun.application.AiCandidateDecisionResult;
import com.fowoco.server.airun.application.AiRunCandidateResult;
import com.fowoco.server.airun.application.error.AiRunErrorCode;
import com.fowoco.server.airun.application.port.AiCandidateDecisionRepository;
import com.fowoco.server.airun.domain.AiCandidateDecisionAction;
import com.fowoco.server.airun.domain.AiRunStatus;
import com.fowoco.server.common.error.ApiException;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcAiCandidateDecisionRepository implements AiCandidateDecisionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAiCandidateDecisionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public DecisionContext lockRun(UUID aiRunId, UUID companyId) {
        RunRow run = jdbcTemplate.query(
                """
                SELECT status, analysis_outcome, detected_intent, version
                FROM ai_run
                WHERE ai_run_id = ? AND company_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNum) -> new RunRow(
                        AiRunStatus.valueOf(resultSet.getString("status")),
                        nullableOutcome(resultSet.getString("analysis_outcome")),
                        resultSet.getString("detected_intent"),
                        resultSet.getLong("version")
                ),
                aiRunId,
                companyId
        ).stream().findFirst().orElseThrow(() -> new ApiException(AiRunErrorCode.AI_RUN_NOT_FOUND));

        List<AiRunCandidateResult> candidates = jdbcTemplate.query(
                """
                SELECT candidate.ai_candidate_id, candidate.candidate_ref,
                       candidate.worker_id, candidate.workflow_id,
                       candidate.extracted_slots_json, candidate.missing_slots_json,
                       candidate.confidence
                FROM ai_candidate candidate
                JOIN ai_attempt attempt
                  ON attempt.ai_attempt_id = candidate.ai_attempt_id
                 AND attempt.company_id = candidate.company_id
                WHERE candidate.ai_run_id = ?
                  AND candidate.company_id = ?
                  AND attempt.sequence_no = (
                      SELECT MAX(latest.sequence_no)
                      FROM ai_attempt latest
                      WHERE latest.ai_run_id = ? AND latest.company_id = ?
                  )
                ORDER BY candidate.created_at, candidate.candidate_ref
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
                aiRunId,
                companyId,
                aiRunId,
                companyId
        );
        return new DecisionContext(
                aiRunId,
                companyId,
                run.status(),
                run.outcome(),
                run.detectedIntent(),
                run.version(),
                candidates
        );
    }

    @Override
    public Optional<StoredBatch> findBatch(
            UUID aiRunId,
            UUID companyId,
            String idempotencyKeyHash
    ) {
        Optional<BatchRow> batch = jdbcTemplate.query(
                """
                SELECT decision_batch_id, payload_hash, case_id, resulting_run_version
                FROM ai_candidate_decision_batch
                WHERE ai_run_id = ? AND company_id = ? AND idempotency_key_hash = ?
                """,
                (resultSet, rowNum) -> new BatchRow(
                        uuid(resultSet, "decision_batch_id"),
                        resultSet.getString("payload_hash"),
                        nullableUuid(resultSet, "case_id"),
                        nullableLong(resultSet, "resulting_run_version")
                ),
                aiRunId,
                companyId,
                idempotencyKeyHash
        ).stream().findFirst();
        return batch.map(row -> storedBatch(row, aiRunId, companyId));
    }

    @Override
    public boolean candidateAlreadyDecided(UUID candidateId, UUID companyId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ai_candidate_decision
                WHERE ai_candidate_id = ? AND company_id = ?
                """,
                Integer.class,
                candidateId,
                companyId
        );
        return count != null && count > 0;
    }

    @Override
    public void insertBatch(NewBatch batch) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_candidate_decision_batch (
                    decision_batch_id, ai_run_id, company_id, decided_by,
                    idempotency_key_hash, payload_hash, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                batch.decisionBatchId(),
                batch.aiRunId(),
                batch.companyId(),
                batch.decidedBy(),
                batch.idempotencyKeyHash(),
                batch.payloadHash(),
                timestamp(batch.createdAt())
        );
    }

    @Override
    public void insertDecision(NewDecision decision) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_candidate_decision (
                    decision_id, decision_batch_id, ai_run_id, ai_candidate_id,
                    company_id, action, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                decision.decisionId(),
                decision.decisionBatchId(),
                decision.aiRunId(),
                decision.candidateId(),
                decision.companyId(),
                decision.action().name(),
                timestamp(decision.createdAt())
        );
    }

    @Override
    public void attachTasks(
            UUID decisionId,
            UUID companyId,
            List<UUID> taskIds,
            Instant createdAt
    ) {
        for (int index = 0; index < taskIds.size(); index++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_candidate_decision_task (
                        decision_id, task_id, company_id, sequence_no, created_at
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    decisionId,
                    taskIds.get(index),
                    companyId,
                    index + 1,
                    timestamp(createdAt)
            );
        }
    }

    @Override
    public long completeBatch(
            UUID decisionBatchId,
            UUID aiRunId,
            UUID companyId,
            UUID caseId,
            long expectedRunVersion,
            Instant completedAt
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE ai_run
                SET updated_at = ?, version = version + 1
                WHERE ai_run_id = ? AND company_id = ? AND version = ?
                """,
                timestamp(completedAt),
                aiRunId,
                companyId,
                expectedRunVersion
        );
        if (updated != 1) {
            throw new ApiException(AiRunErrorCode.AI_RUN_VERSION_CONFLICT);
        }
        long resultingVersion = expectedRunVersion + 1;
        jdbcTemplate.update(
                """
                UPDATE ai_candidate_decision_batch
                SET case_id = ?, resulting_run_version = ?, completed_at = ?
                WHERE decision_batch_id = ? AND ai_run_id = ? AND company_id = ?
                """,
                caseId,
                resultingVersion,
                timestamp(completedAt),
                decisionBatchId,
                aiRunId,
                companyId
        );
        return resultingVersion;
    }

    private StoredBatch storedBatch(BatchRow batch, UUID aiRunId, UUID companyId) {
        UUID batchId = batch.batchId();
        Long resultingVersion = batch.resultingRunVersion();
        if (resultingVersion == null) {
            throw new IllegalStateException("candidate decision batch is incomplete");
        }
        List<AiCandidateDecisionResult.Decision> decisions = jdbcTemplate.query(
                """
                SELECT ai_candidate_id, action
                FROM ai_candidate_decision
                WHERE decision_batch_id = ? AND company_id = ?
                ORDER BY created_at, ai_candidate_id
                """,
                (decisionSet, rowNum) -> new AiCandidateDecisionResult.Decision(
                        uuid(decisionSet, "ai_candidate_id"),
                        AiCandidateDecisionAction.valueOf(decisionSet.getString("action"))
                ),
                batchId,
                companyId
        );
        List<UUID> taskIds = jdbcTemplate.query(
                """
                SELECT decision_task.task_id
                FROM ai_candidate_decision_task decision_task
                JOIN ai_candidate_decision decision
                  ON decision.decision_id = decision_task.decision_id
                 AND decision.company_id = decision_task.company_id
                WHERE decision.decision_batch_id = ? AND decision.company_id = ?
                ORDER BY decision_task.sequence_no
                """,
                (taskSet, rowNum) -> uuid(taskSet, "task_id"),
                batchId,
                companyId
        );
        return new StoredBatch(
                batch.payloadHash(),
                new AiCandidateDecisionResult(
                        batchId,
                        aiRunId,
                        batch.caseId(),
                        taskIds,
                        decisions,
                        resultingVersion
                )
        );
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

    private AiAnalysisOutcome nullableOutcome(String value) {
        return value == null ? null : AiAnalysisOutcome.valueOf(value);
    }

    private UUID nullableUuid(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return Optional.ofNullable(nullableUuid(resultSet, column))
                .orElseThrow(() -> new SQLException(column + " must not be null"));
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private record RunRow(
            AiRunStatus status,
            AiAnalysisOutcome outcome,
            String detectedIntent,
            long version
    ) {
    }

    private record BatchRow(
            UUID batchId,
            String payloadHash,
            UUID caseId,
            Long resultingRunVersion
    ) {
    }
}
