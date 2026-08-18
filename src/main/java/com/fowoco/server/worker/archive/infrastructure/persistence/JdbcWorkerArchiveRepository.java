package com.fowoco.server.worker.archive.infrastructure.persistence;

import com.fowoco.server.worker.archive.application.WorkerArchiveBlocker;
import com.fowoco.server.worker.archive.application.port.WorkerArchiveRepository;
import com.fowoco.server.worker.archive.domain.WorkerArchive;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkerArchiveRepository implements WorkerArchiveRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkerArchiveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean lockWorker(UUID workerId, UUID companyId) {
        return !jdbcTemplate.query(
                """
                SELECT worker_id
                  FROM worker
                 WHERE worker_id = ? AND company_id = ?
                 FOR UPDATE
                """,
                (resultSet, rowNumber) -> resultSet.getObject("worker_id", UUID.class),
                workerId,
                companyId
        ).isEmpty();
    }

    @Override
    public Optional<WorkerArchive> find(UUID workerId, UUID companyId) {
        return jdbcTemplate.query(
                        """
                        SELECT worker_id, company_id, archived_at, archived_by,
                               archive_reason, worker_version
                          FROM worker_archive
                         WHERE worker_id = ? AND company_id = ?
                        """,
                        this::map,
                        workerId,
                        companyId
                )
                .stream()
                .findFirst();
    }

    @Override
    public List<WorkerArchiveBlocker> findOperationalBlockers(
            UUID workerId,
            UUID companyId,
            Instant now
    ) {
        List<WorkerArchiveBlocker> blockers = new ArrayList<>();
        if (count(
                """
                SELECT COUNT(*) FROM task
                 WHERE worker_id = ? AND company_id = ?
                   AND status NOT IN ('COMPLETED', 'CANCELLED')
                """,
                workerId,
                companyId
        ) > 0) {
            blockers.add(WorkerArchiveBlocker.OPEN_TASK);
        }
        if (count(
                """
                SELECT COUNT(*)
                  FROM approval_request approval
                  JOIN task ON task.task_id = approval.task_id AND task.company_id = approval.company_id
                 WHERE task.worker_id = ? AND task.company_id = ? AND approval.status = 'PENDING'
                """,
                workerId,
                companyId
        ) > 0) {
            blockers.add(WorkerArchiveBlocker.PENDING_APPROVAL);
        }
        if (count(
                """
                SELECT COUNT(*)
                  FROM worker_link link
                  JOIN task ON task.task_id = link.task_id AND task.company_id = link.company_id
                 WHERE task.worker_id = ? AND task.company_id = ?
                   AND link.status = 'ACTIVE' AND link.expires_at > ?
                """,
                workerId,
                companyId,
                Timestamp.from(now)
        ) > 0) {
            blockers.add(WorkerArchiveBlocker.ACTIVE_WORKER_LINK);
        }
        return List.copyOf(blockers);
    }

    @Override
    public boolean reserveWorkerVersion(
            UUID workerId,
            UUID companyId,
            long expectedVersion,
            Instant now
    ) {
        return jdbcTemplate.update(
                """
                UPDATE worker
                   SET updated_at = ?, version = version + 1
                 WHERE worker_id = ? AND company_id = ? AND version = ?
                """,
                Timestamp.from(now),
                workerId,
                companyId,
                expectedVersion
        ) == 1;
    }

    @Override
    public void insert(WorkerArchive archive) {
        jdbcTemplate.update(
                """
                INSERT INTO worker_archive (
                    worker_id, company_id, archived_at, archived_by,
                    archive_reason, worker_version
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                archive.workerId(),
                archive.companyId(),
                Timestamp.from(archive.archivedAt()),
                archive.archivedBy(),
                archive.archiveReason(),
                archive.workerVersion()
        );
    }

    private long count(String sql, Object... parameters) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return value == null ? 0 : value;
    }

    private WorkerArchive map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkerArchive(
                resultSet.getObject("worker_id", UUID.class),
                resultSet.getObject("company_id", UUID.class),
                resultSet.getTimestamp("archived_at").toInstant(),
                resultSet.getObject("archived_by", UUID.class),
                resultSet.getString("archive_reason"),
                resultSet.getLong("worker_version")
        );
    }
}
