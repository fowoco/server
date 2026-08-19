package com.fowoco.server.auth.infrastructure.crypto;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class AccountPiiMaintenanceService {

    private static final String PHONE_FIELD = "phone";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final AccountPiiCipher piiCipher;

    AccountPiiMaintenanceService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            AccountPiiCipher piiCipher
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(
                transactionManager,
                "transactionManager must not be null"
        ));
        this.piiCipher = Objects.requireNonNull(piiCipher, "piiCipher must not be null");
    }

    MaintenanceResult migrateToCurrentKey(int batchSize) {
        requireReady(batchSize);
        requireMaintenanceDatabaseRole();
        int migrated = processBatches(batchSize, this::migrateBatch);
        EncryptionInventory inventory = inspect();
        if (inventory.plaintextCount() != 0 || inventory.staleKeyCount() != 0) {
            throw new IllegalStateException(
                    "account PII migration finished with plaintext or stale-key rows remaining"
            );
        }
        return new MaintenanceResult(migrated, inventory);
    }

    MaintenanceResult restorePlaintext(int batchSize) {
        requireReady(batchSize);
        requireMaintenanceDatabaseRole();
        int restored = processBatches(batchSize, this::restoreBatch);
        EncryptionInventory inventory = inspect();
        if (inventory.encryptedCount() != 0) {
            throw new IllegalStateException(
                    "account PII plaintext restore finished with encrypted rows remaining"
            );
        }
        return new MaintenanceResult(restored, inventory);
    }

    EncryptionInventory verifyCurrentKey() {
        requireReady(1);
        requireMaintenanceDatabaseRole();
        EncryptionInventory inventory = inspect();
        if (inventory.plaintextCount() != 0 || inventory.staleKeyCount() != 0) {
            throw new IllegalStateException(
                    "account PII verification failed because plaintext or stale-key rows remain"
            );
        }
        return inventory;
    }

    private int processBatches(int batchSize, BatchOperation operation) {
        int processed = 0;
        while (true) {
            Integer batchProcessed = transactionTemplate.execute(status -> operation.run(batchSize));
            int changed = batchProcessed == null ? 0 : batchProcessed;
            if (changed == 0) {
                return processed;
            }
            processed += changed;
        }
    }

    private int migrateBatch(int batchSize) {
        List<PhoneRow> rows = jdbcTemplate.query(
                """
                SELECT user_id, company_id, phone, phone_ciphertext, phone_key_version
                FROM user_account
                WHERE phone IS NOT NULL
                   OR (phone_ciphertext IS NOT NULL AND phone_key_version <> ?)
                ORDER BY user_id
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new PhoneRow(
                        resultSet.getObject("user_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class),
                        resultSet.getString("phone"),
                        resultSet.getString("phone_ciphertext"),
                        resultSet.getString("phone_key_version")
                ),
                piiCipher.currentKeyVersion(),
                batchSize
        );
        int updated = 0;
        for (PhoneRow row : rows) {
            String plaintext = row.phone() != null
                    ? row.phone()
                    : piiCipher.decrypt(
                            row.phoneCiphertext(),
                            row.phoneKeyVersion(),
                            row.companyId(),
                            row.userId(),
                            PHONE_FIELD
                    );
            AccountPiiCipher.EncryptedValue encrypted = piiCipher.encrypt(
                    plaintext,
                    row.companyId(),
                    row.userId(),
                    PHONE_FIELD
            );
            updated += row.phone() != null
                    ? replacePlaintext(row, encrypted)
                    : replaceStaleCiphertext(row, encrypted);
        }
        ensureBatchMadeProgress(rows, updated);
        return updated;
    }

    private int restoreBatch(int batchSize) {
        List<PhoneRow> rows = jdbcTemplate.query(
                """
                SELECT user_id, company_id, phone, phone_ciphertext, phone_key_version
                FROM user_account
                WHERE phone_ciphertext IS NOT NULL
                ORDER BY user_id
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new PhoneRow(
                        resultSet.getObject("user_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class),
                        resultSet.getString("phone"),
                        resultSet.getString("phone_ciphertext"),
                        resultSet.getString("phone_key_version")
                ),
                batchSize
        );
        int updated = 0;
        for (PhoneRow row : rows) {
            String plaintext = piiCipher.decrypt(
                    row.phoneCiphertext(),
                    row.phoneKeyVersion(),
                    row.companyId(),
                    row.userId(),
                    PHONE_FIELD
            );
            updated += jdbcTemplate.update(
                    """
                    UPDATE user_account
                    SET phone = ?,
                        phone_ciphertext = NULL,
                        phone_key_version = NULL,
                        updated_at = CURRENT_TIMESTAMP,
                        version = version + 1
                    WHERE user_id = ?
                      AND company_id = ?
                      AND phone IS NULL
                      AND phone_ciphertext = ?
                      AND phone_key_version = ?
                    """,
                    plaintext,
                    row.userId(),
                    row.companyId(),
                    row.phoneCiphertext(),
                    row.phoneKeyVersion()
            );
        }
        ensureBatchMadeProgress(rows, updated);
        return updated;
    }

    private int replacePlaintext(
            PhoneRow row,
            AccountPiiCipher.EncryptedValue encrypted
    ) {
        return jdbcTemplate.update(
                """
                UPDATE user_account
                SET phone = NULL,
                    phone_ciphertext = ?,
                    phone_key_version = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE user_id = ?
                  AND company_id = ?
                  AND phone = ?
                  AND phone_ciphertext IS NULL
                  AND phone_key_version IS NULL
                """,
                encrypted.ciphertext(),
                encrypted.keyVersion(),
                row.userId(),
                row.companyId(),
                row.phone()
        );
    }

    private int replaceStaleCiphertext(
            PhoneRow row,
            AccountPiiCipher.EncryptedValue encrypted
    ) {
        return jdbcTemplate.update(
                """
                UPDATE user_account
                SET phone_ciphertext = ?,
                    phone_key_version = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE user_id = ?
                  AND company_id = ?
                  AND phone IS NULL
                  AND phone_ciphertext = ?
                  AND phone_key_version = ?
                """,
                encrypted.ciphertext(),
                encrypted.keyVersion(),
                row.userId(),
                row.companyId(),
                row.phoneCiphertext(),
                row.phoneKeyVersion()
        );
    }

    private EncryptionInventory inspect() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS account_count,
                       SUM(CASE WHEN phone IS NOT NULL THEN 1 ELSE 0 END) AS plaintext_count,
                       SUM(CASE WHEN phone_ciphertext IS NOT NULL THEN 1 ELSE 0 END) AS encrypted_count,
                       SUM(CASE
                               WHEN phone_ciphertext IS NOT NULL AND phone_key_version = ? THEN 1
                               ELSE 0
                           END) AS current_key_count,
                       SUM(CASE
                               WHEN phone_ciphertext IS NOT NULL AND phone_key_version <> ? THEN 1
                               ELSE 0
                           END) AS stale_key_count
                FROM user_account
                """,
                (resultSet, rowNumber) -> new EncryptionInventory(
                        resultSet.getLong("account_count"),
                        resultSet.getLong("plaintext_count"),
                        resultSet.getLong("encrypted_count"),
                        resultSet.getLong("current_key_count"),
                        resultSet.getLong("stale_key_count"),
                        piiCipher.currentKeyVersion()
                ),
                piiCipher.currentKeyVersion(),
                piiCipher.currentKeyVersion()
        );
    }

    private void requireReady(int batchSize) {
        if (!piiCipher.isAvailable()) {
            throw new IllegalStateException(
                    "account PII encryption must be enabled for maintenance commands"
            );
        }
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalStateException("account PII maintenance batch size must be 1 to 1000");
        }
    }

    private void requireMaintenanceDatabaseRole() {
        String databaseProduct = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> databaseProductName(connection)
        );
        if (!"PostgreSQL".equalsIgnoreCase(databaseProduct)) {
            return;
        }
        Boolean allowed = jdbcTemplate.queryForObject(
                """
                SELECT role.rolsuper
                       OR role.rolbypassrls
                       OR account_table.relowner = role.oid
                FROM pg_catalog.pg_roles AS role
                JOIN pg_catalog.pg_class AS account_table
                  ON account_table.relname = 'user_account'
                JOIN pg_catalog.pg_namespace AS namespace
                  ON namespace.oid = account_table.relnamespace
                 AND namespace.nspname = 'public'
                WHERE role.rolname = CURRENT_USER
                """,
                Boolean.class
        );
        if (!Boolean.TRUE.equals(allowed)) {
            throw new IllegalStateException(
                    "account PII maintenance requires the migration owner or a BYPASSRLS role"
            );
        }
    }

    private String databaseProductName(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName();
    }

    private void ensureBatchMadeProgress(List<PhoneRow> rows, int updated) {
        if (!rows.isEmpty() && updated == 0) {
            throw new IllegalStateException(
                    "account PII maintenance made no progress because rows changed concurrently"
            );
        }
    }

    record MaintenanceResult(int processedCount, EncryptionInventory inventory) {
    }

    record EncryptionInventory(
            long accountCount,
            long plaintextCount,
            long encryptedCount,
            long currentKeyCount,
            long staleKeyCount,
            String currentKeyVersion
    ) {
    }

    private record PhoneRow(
            UUID userId,
            UUID companyId,
            String phone,
            String phoneCiphertext,
            String phoneKeyVersion
    ) {
    }

    @FunctionalInterface
    private interface BatchOperation {
        int run(int batchSize);
    }
}
