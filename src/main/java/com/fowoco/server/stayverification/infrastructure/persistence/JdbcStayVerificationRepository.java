package com.fowoco.server.stayverification.infrastructure.persistence;

import com.fowoco.server.stayverification.application.StayVerificationCommand;
import com.fowoco.server.stayverification.application.port.StayVerificationRepository;
import com.fowoco.server.stayverification.domain.StayVerificationCase;
import com.fowoco.server.stayverification.domain.StayVerificationStatus;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStayVerificationRepository implements StayVerificationRepository {

    private static final String SELECT_CASE = """
            SELECT verification.stay_verification_id, verification.company_id,
                   verification.worker_id, worker.display_name,
                   verification.source_stay_expiry_date, verification.verification_status,
                   verification.status_checked_at, verification.extension_applied_at,
                   verification.extension_receipt_document_id,
                   verification.approval_result_document_id,
                   verification.new_stay_expiry_date,
                   verification.official_consultation_note,
                   verification.employment_end_confirmed_at,
                   verification.recheck_date, verification.created_at,
                   verification.updated_at, verification.version
              FROM stay_verification_case verification
              JOIN worker
                ON worker.worker_id = verification.worker_id
               AND worker.company_id = verification.company_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcStayVerificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ExpiredWorker> findExpiredWorkers(LocalDate today) {
        return jdbcTemplate.query(
                """
                SELECT company_id, worker_id, display_name, stay_expiry_date
                  FROM worker
                 WHERE stay_expiry_date < ?
                   AND work_status IN ('ACTIVE', 'ON_LEAVE')
                 ORDER BY company_id, worker_id
                """,
                this::mapExpiredWorker,
                Date.valueOf(today)
        );
    }

    @Override
    public List<ExpiredWorker> findExpiredWorkers(UUID companyId, LocalDate today) {
        return jdbcTemplate.query(
                """
                SELECT company_id, worker_id, display_name, stay_expiry_date
                  FROM worker
                 WHERE company_id = ?
                   AND stay_expiry_date < ?
                   AND work_status IN ('ACTIVE', 'ON_LEAVE')
                 ORDER BY worker_id
                """,
                this::mapExpiredWorker,
                companyId,
                Date.valueOf(today)
        );
    }

    @Override
    public boolean insertIfAbsent(UUID verificationId, ExpiredWorker worker, Instant now) {
        return jdbcTemplate.update(
                    """
                    INSERT INTO stay_verification_case (
                        stay_verification_id, company_id, worker_id,
                        source_stay_expiry_date, verification_status,
                        created_at, updated_at, version
                    )
                    SELECT ?, ?, ?, ?, 'UNKNOWN', ?, ?, 0
                     WHERE NOT EXISTS (
                         SELECT 1
                           FROM stay_verification_case
                          WHERE company_id = ?
                            AND worker_id = ?
                            AND source_stay_expiry_date = ?
                     )
                    """,
                    verificationId,
                    worker.companyId(),
                    worker.workerId(),
                    Date.valueOf(worker.stayExpiryDate()),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    worker.companyId(),
                    worker.workerId(),
                    Date.valueOf(worker.stayExpiryDate())
        ) == 1;
    }

    @Override
    public List<StayVerificationCase> findAll(UUID companyId, StayVerificationStatus status) {
        String statusFilter = status == null ? "" : " AND verification.verification_status = ?";
        String sql = SELECT_CASE
                + " WHERE verification.company_id = ?"
                + statusFilter
                + " ORDER BY verification.source_stay_expiry_date ASC, verification.created_at ASC";
        return status == null
                ? jdbcTemplate.query(sql, this::mapCase, companyId)
                : jdbcTemplate.query(sql, this::mapCase, companyId, status.name());
    }

    @Override
    public Optional<StayVerificationCase> findById(UUID verificationId, UUID companyId) {
        return jdbcTemplate.query(
                        SELECT_CASE + " WHERE verification.stay_verification_id = ? AND verification.company_id = ?",
                        this::mapCase,
                        verificationId,
                        companyId
                )
                .stream()
                .findFirst();
    }

    @Override
    public boolean update(StayVerificationCommand command, UUID companyId, Instant checkedAt, Instant now) {
        return jdbcTemplate.update(
                """
                UPDATE stay_verification_case
                   SET verification_status = ?, status_checked_at = ?,
                       extension_applied_at = ?, extension_receipt_document_id = ?,
                       approval_result_document_id = ?, new_stay_expiry_date = ?,
                       official_consultation_note = ?, employment_end_confirmed_at = ?,
                       recheck_date = ?, updated_at = ?, version = version + 1
                 WHERE stay_verification_id = ? AND company_id = ? AND version = ?
                """,
                command.status().name(),
                Timestamp.from(checkedAt),
                nullableDate(command.extensionAppliedAt()),
                command.extensionReceiptDocumentId(),
                command.approvalResultDocumentId(),
                nullableDate(command.newStayExpiryDate()),
                normalize(command.officialConsultationNote()),
                nullableTimestamp(command.employmentEndConfirmedAt()),
                nullableDate(command.recheckDate()),
                Timestamp.from(now),
                command.stayVerificationId(),
                companyId,
                command.expectedVersion()
        ) == 1;
    }

    private ExpiredWorker mapExpiredWorker(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ExpiredWorker(
                resultSet.getObject("company_id", UUID.class),
                resultSet.getObject("worker_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getDate("stay_expiry_date").toLocalDate()
        );
    }

    private StayVerificationCase mapCase(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StayVerificationCase(
                resultSet.getObject("stay_verification_id", UUID.class),
                resultSet.getObject("company_id", UUID.class),
                resultSet.getObject("worker_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getDate("source_stay_expiry_date").toLocalDate(),
                StayVerificationStatus.valueOf(resultSet.getString("verification_status")),
                instant(resultSet, "status_checked_at"),
                localDate(resultSet, "extension_applied_at"),
                resultSet.getObject("extension_receipt_document_id", UUID.class),
                resultSet.getObject("approval_result_document_id", UUID.class),
                localDate(resultSet, "new_stay_expiry_date"),
                resultSet.getString("official_consultation_note"),
                instant(resultSet, "employment_end_confirmed_at"),
                localDate(resultSet, "recheck_date"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version")
        );
    }

    private static LocalDate localDate(ResultSet resultSet, String column) throws SQLException {
        Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Date nullableDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static Timestamp nullableTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
