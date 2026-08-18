package com.fowoco.server.worker.infrastructure;

import com.fowoco.server.worker.application.WorkerTaskContext;
import com.fowoco.server.worker.application.port.WorkerTaskContextReader;
import com.fowoco.server.worker.domain.WorkerStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkerTaskContextReader implements WorkerTaskContextReader {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkerTaskContextReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<WorkerTaskContext> findByIdAndCompanyId(UUID workerId, UUID companyId) {
        List<UUID> lockedWorkerIds = jdbcTemplate.query(
                """
                SELECT worker_id
                  FROM worker
                 WHERE worker_id = ?
                   AND company_id = ?
                 FOR UPDATE
                """,
                (resultSet, rowNumber) -> resultSet.getObject("worker_id", UUID.class),
                workerId,
                companyId
        );
        if (lockedWorkerIds.isEmpty()) {
            return Optional.empty();
        }

        return findContext(workerId, companyId);
    }

    @Override
    public Optional<WorkerTaskContext> findByIdAndCompanyIdReadOnly(
            UUID workerId,
            UUID companyId
    ) {
        return findContext(workerId, companyId);
    }

    private Optional<WorkerTaskContext> findContext(UUID workerId, UUID companyId) {
        List<WorkerTaskContext> rows = jdbcTemplate.query(
                """
                SELECT worker_id, work_status, stay_expiry_date,
                       contract_start_date, contract_end_date
                  FROM worker
                 WHERE worker_id = ?
                   AND company_id = ?
                   AND NOT EXISTS (
                       SELECT 1
                         FROM worker_archive archive
                        WHERE archive.worker_id = worker.worker_id
                          AND archive.company_id = worker.company_id
                   )
                """,
                (resultSet, rowNumber) -> new WorkerTaskContext(
                        resultSet.getObject("worker_id", UUID.class),
                        WorkerStatus.valueOf(resultSet.getString("work_status")),
                        resultSet.getObject("stay_expiry_date", java.time.LocalDate.class),
                        resultSet.getObject("contract_start_date", java.time.LocalDate.class),
                        resultSet.getObject("contract_end_date", java.time.LocalDate.class)
                ),
                workerId,
                companyId
        );
        return rows.stream().findFirst();
    }
}
