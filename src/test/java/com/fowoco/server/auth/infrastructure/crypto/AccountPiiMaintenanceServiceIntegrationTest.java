package com.fowoco.server.auth.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.auth.infrastructure.crypto.AccountPiiMaintenanceService.MaintenanceResult;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fowoco-account-pii-maintenance-test;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.auth.pii.enabled=true",
        "app.auth.pii.current-key-base64=AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.auth.pii.current-key-version=pii-v2",
        "app.auth.pii.decryption-keys=pii-v1=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountPiiMaintenanceServiceIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID PLAINTEXT_USER_ID = UUID.fromString(
            "11000000-0000-0000-0000-000000000001"
    );
    private static final UUID OLD_KEY_USER_ID = UUID.fromString(
            "11000000-0000-0000-0000-000000000002"
    );
    private static final String PLAINTEXT_PHONE = "010-1111-2222";
    private static final String OLD_KEY_PHONE = "010-3333-4444";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountPiiMaintenanceService service;

    @Autowired
    private AccountPiiCipher currentCipher;

    @BeforeEach
    void prepareRows() {
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
        jdbcTemplate.update(
                """
                INSERT INTO company (
                    company_id, name, status, created_at, updated_at, version
                ) VALUES (?, '테스트 사업장', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                COMPANY_ID
        );
        insertAccount(PLAINTEXT_USER_ID, "plain@example.com", PLAINTEXT_PHONE, null, null);

        AccountPiiCipher oldCipher = oldCipher();
        AccountPiiCipher.EncryptedValue oldEncrypted = oldCipher.encrypt(
                OLD_KEY_PHONE,
                COMPANY_ID,
                OLD_KEY_USER_ID,
                "phone"
        );
        insertAccount(
                OLD_KEY_USER_ID,
                "old-key@example.com",
                null,
                oldEncrypted.ciphertext(),
                oldEncrypted.keyVersion()
        );
    }

    @Test
    void migratesPlaintextAndOldKeyRowsThenRestoresPlaintextForRollback() {
        MaintenanceResult migrated = service.migrateToCurrentKey(1);

        assertThat(migrated.processedCount()).isEqualTo(2);
        assertThat(migrated.inventory().plaintextCount()).isZero();
        assertThat(migrated.inventory().currentKeyCount()).isEqualTo(2);
        assertThat(migrated.inventory().staleKeyCount()).isZero();
        assertThat(service.migrateToCurrentKey(1).processedCount()).isZero();
        assertStoredPhone(PLAINTEXT_USER_ID, PLAINTEXT_PHONE);
        assertStoredPhone(OLD_KEY_USER_ID, OLD_KEY_PHONE);

        MaintenanceResult restored = service.restorePlaintext(1);

        assertThat(restored.processedCount()).isEqualTo(2);
        assertThat(restored.inventory().encryptedCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT phone FROM user_account WHERE user_id = ?",
                String.class,
                PLAINTEXT_USER_ID
        )).isEqualTo(PLAINTEXT_PHONE);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT phone FROM user_account WHERE user_id = ?",
                String.class,
                OLD_KEY_USER_ID
        )).isEqualTo(OLD_KEY_PHONE);
    }

    @Test
    void verificationFailsWhilePlaintextOrOldKeyRowsRemain() {
        assertThatThrownBy(service::verifyCurrentKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plaintext or stale-key rows remain");
    }

    private void insertAccount(
            UUID userId,
            String email,
            String phone,
            String ciphertext,
            String keyVersion
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, display_name, phone, phone_ciphertext,
                    phone_key_version, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, password_changed_at,
                    failed_login_attempts, version
                ) VALUES (
                    ?, ?, '담당자', ?, ?, ?, ?, ?, 'password-hash',
                    'HR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, 0, 0
                )
                """,
                userId,
                COMPANY_ID,
                phone,
                ciphertext,
                keyVersion,
                email,
                email
        );
    }

    private void assertStoredPhone(UUID userId, String expected) {
        StoredPhone stored = jdbcTemplate.queryForObject(
                """
                SELECT phone, phone_ciphertext, phone_key_version
                FROM user_account
                WHERE user_id = ?
                """,
                (resultSet, rowNumber) -> new StoredPhone(
                        resultSet.getString("phone"),
                        resultSet.getString("phone_ciphertext"),
                        resultSet.getString("phone_key_version")
                ),
                userId
        );
        assertThat(stored).isNotNull();
        assertThat(stored.plaintext()).isNull();
        assertThat(stored.keyVersion()).isEqualTo("pii-v2");
        assertThat(currentCipher.decrypt(
                stored.ciphertext(),
                stored.keyVersion(),
                COMPANY_ID,
                userId,
                "phone"
        )).isEqualTo(expected);
    }

    private AccountPiiCipher oldCipher() {
        AccountPiiProperties properties = new AccountPiiProperties();
        properties.setEnabled(true);
        properties.setCurrentKeyVersion("pii-v1");
        properties.setCurrentKeyBase64(
                Base64.getEncoder().encodeToString(new byte[32])
        );
        return new AccountPiiCryptoConfiguration().accountPiiCipher(properties);
    }

    private record StoredPhone(String plaintext, String ciphertext, String keyVersion) {
    }
}
