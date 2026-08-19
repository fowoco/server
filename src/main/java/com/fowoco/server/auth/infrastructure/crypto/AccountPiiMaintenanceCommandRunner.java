package com.fowoco.server.auth.infrastructure.crypto;

import com.fowoco.server.auth.infrastructure.crypto.AccountPiiMaintenanceService.EncryptionInventory;
import com.fowoco.server.auth.infrastructure.crypto.AccountPiiMaintenanceService.MaintenanceResult;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class AccountPiiMaintenanceCommandRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            AccountPiiMaintenanceCommandRunner.class
    );

    private final AccountPiiMaintenanceService service;
    private final ConfigurableApplicationContext applicationContext;
    private final String command;
    private final int batchSize;

    AccountPiiMaintenanceCommandRunner(
            AccountPiiMaintenanceService service,
            ConfigurableApplicationContext applicationContext,
            @Value("${app.auth.pii.maintenance-command:none}") String command,
            @Value("${app.auth.pii.maintenance-batch-size:100}") int batchSize
    ) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.applicationContext = Objects.requireNonNull(
                applicationContext,
                "applicationContext must not be null"
        );
        this.command = Objects.requireNonNull(command, "command must not be null");
        this.batchSize = batchSize;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Command parsed = Command.parse(command);
        if (parsed == Command.NONE) {
            return;
        }
        switch (parsed) {
            case MIGRATE -> logResult(parsed, service.migrateToCurrentKey(batchSize));
            case VERIFY -> logInventory(parsed, service.verifyCurrentKey(), 0);
            case RESTORE_PLAINTEXT -> logResult(parsed, service.restorePlaintext(batchSize));
            case NONE -> throw new IllegalStateException("unreachable account PII command");
        }
        applicationContext.close();
    }

    private void logResult(Command command, MaintenanceResult result) {
        logInventory(command, result.inventory(), result.processedCount());
    }

    private void logInventory(
            Command command,
            EncryptionInventory inventory,
            int processedCount
    ) {
        LOGGER.info(
                "account_pii_maintenance command={} processed_count={} account_count={} "
                        + "plaintext_count={} encrypted_count={} current_key_count={} "
                        + "stale_key_count={} current_key_version={}",
                command.externalName(),
                processedCount,
                inventory.accountCount(),
                inventory.plaintextCount(),
                inventory.encryptedCount(),
                inventory.currentKeyCount(),
                inventory.staleKeyCount(),
                inventory.currentKeyVersion()
        );
    }

    private enum Command {
        NONE("none"),
        MIGRATE("migrate"),
        VERIFY("verify"),
        RESTORE_PLAINTEXT("restore-plaintext");

        private final String externalName;

        Command(String externalName) {
            this.externalName = externalName;
        }

        String externalName() {
            return externalName;
        }

        static Command parse(String value) {
            String normalized = value.strip().toLowerCase(Locale.ROOT);
            for (Command command : values()) {
                if (command.externalName.equals(normalized)) {
                    return command;
                }
            }
            throw new IllegalStateException(
                    "app.auth.pii.maintenance-command must be one of "
                            + "none, migrate, verify, restore-plaintext"
            );
        }
    }
}
