package com.fowoco.server.demo.infrastructure.seed;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class DemoGoldenFlowSeedStateGuard {

    private static final String RESET_REQUIRED_MESSAGE =
            "legacy Golden Flow demo seed rows detected; reset the personal demo database or "
                    + "volume before starting with the Issue #94 seed contract";

    private final JdbcTemplate jdbcTemplate;

    DemoGoldenFlowSeedStateGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    void verifyNoLegacyRows(DemoOperationalSeedContext context) {
        int legacyRowCount = count(
                "workflow_case",
                "case_id",
                context.companyId(),
                List.of(DemoOperationalSeedCatalog.RETIRED_GOLDEN_FLOW_CASE_ID)
        ) + count(
                "task",
                "task_id",
                context.companyId(),
                DemoOperationalSeedCatalog.RETIRED_GOLDEN_FLOW_TASK_IDS.stream().toList()
        ) + count(
                "worker_document",
                "worker_document_id",
                context.companyId(),
                DemoOperationalSeedCatalog.RETIRED_GOLDEN_FLOW_DOCUMENT_IDS.stream().toList()
        ) + count(
                "audit_event",
                "audit_event_id",
                context.companyId(),
                DemoOperationalSeedCatalog.RETIRED_GOLDEN_FLOW_AUDIT_IDS.stream().toList()
        );
        if (legacyRowCount > 0) {
            throw new IllegalStateException(RESET_REQUIRED_MESSAGE);
        }
    }

    private int count(String table, String idColumn, UUID companyId, List<UUID> ids) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] parameters = new Object[ids.size() + 1];
        parameters[0] = companyId;
        for (int index = 0; index < ids.size(); index++) {
            parameters[index + 1] = ids.get(index);
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE company_id = ? AND "
                        + idColumn + " IN (" + placeholders + ")",
                Integer.class,
                parameters
        );
        return count == null ? 0 : count;
    }
}
