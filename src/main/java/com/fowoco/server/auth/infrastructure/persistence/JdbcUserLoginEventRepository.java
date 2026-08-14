package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.application.port.UserLoginEventRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserLoginEventRepository implements UserLoginEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserLoginEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(UUID loginEventId, UUID userId, UUID companyId, String deviceSummary, Instant loggedInAt) {
        jdbcTemplate.update(
                """
                INSERT INTO user_login_event (login_event_id, user_id, company_id, device_summary, logged_in_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                loginEventId,
                userId,
                companyId,
                deviceSummary,
                Timestamp.from(loggedInAt)
        );
    }

    @Override
    public List<LoginEventRecord> findRecent(UUID userId, UUID companyId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT device_summary, logged_in_at
                FROM user_login_event
                WHERE user_id = ? AND company_id = ?
                ORDER BY logged_in_at DESC
                LIMIT ?
                """,
                (resultSet, rowNum) -> mapRow(resultSet),
                userId,
                companyId,
                limit
        );
    }

    private LoginEventRecord mapRow(ResultSet resultSet) throws SQLException {
        return new LoginEventRecord(
                resultSet.getString("device_summary"),
                resultSet.getTimestamp("logged_in_at").toInstant()
        );
    }
}
