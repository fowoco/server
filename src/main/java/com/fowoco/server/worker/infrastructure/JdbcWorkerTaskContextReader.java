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
        List<WorkerTaskContext> rows = jdbcTemplate.query(
                """
                SELECT worker_id, work_status, stay_expiry_date,
                       contract_start_date, contract_end_date
                  FROM worker
                 WHERE worker_id = ?
                   AND company_id = ?
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
