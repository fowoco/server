package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class TenantTransactionExecutorTest {

    private static final UUID COMPANY_A =
            UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B =
            UUID.fromString("b0000000-0000-0000-0000-000000000002");

    private JdbcTemplate jdbcTemplate;
    private DataSourceTransactionManager transactionManager;
    private AtomicReference<UUID> boundCompany;
    private TenantTransactionExecutor executor;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tenant-transaction-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE seed_probe (company_id UUID PRIMARY KEY)");
        transactionManager = new DataSourceTransactionManager(dataSource);
        boundCompany = new AtomicReference<>();
        executor = new TenantTransactionExecutor(transactionManager, companyId -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            boundCompany.set(companyId);
        });
    }

    @Test
    void bindsTenantBeforeWorkAndRollsBackOnlyTheFailingCompany() {
        executor.execute(COMPANY_A, () -> {
            assertThat(boundCompany).hasValue(COMPANY_A);
            jdbcTemplate.update("INSERT INTO seed_probe (company_id) VALUES (?)", COMPANY_A);
        });

        assertThatThrownBy(() -> executor.execute(COMPANY_B, () -> {
            assertThat(boundCompany).hasValue(COMPANY_B);
            jdbcTemplate.update("INSERT INTO seed_probe (company_id) VALUES (?)", COMPANY_B);
            throw new ExpectedFailure();
        })).isInstanceOf(ExpectedFailure.class);

        assertThat(jdbcTemplate.queryForList(
                "SELECT company_id FROM seed_probe ORDER BY company_id",
                UUID.class
        )).containsExactly(COMPANY_A);
    }

    @Test
    void commitsInRequiresNewEvenWhenTheCallingTransactionRollsBack() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        outer.executeWithoutResult(status -> {
            executor.execute(COMPANY_A, () -> jdbcTemplate.update(
                    "INSERT INTO seed_probe (company_id) VALUES (?)",
                    COMPANY_A
            ));
            status.setRollbackOnly();
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seed_probe WHERE company_id = ?",
                Integer.class,
                COMPANY_A
        )).isEqualTo(1);
    }

    private static final class ExpectedFailure extends RuntimeException {
    }
}
