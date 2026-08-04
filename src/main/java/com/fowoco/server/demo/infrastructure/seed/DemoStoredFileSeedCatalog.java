package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.StoredFileSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import java.util.List;
import java.util.UUID;

final class DemoStoredFileSeedCatalog {

    static final UUID CONTRACT_FILE_ID = demoUuid(1);
    static final UUID STAY_RECEIPT_FILE_ID = demoUuid(2);
    static final UUID STAY_RESULT_FILE_ID = demoUuid(3);

    private DemoStoredFileSeedCatalog() {
    }

    static List<StoredFileSeed> demoStoredFiles(List<TaskSeed> tasks) {
        return List.of(
                storedFile(
                        CONTRACT_FILE_ID,
                        tasks,
                        5,
                        "demo-contract-renewal.pdf",
                        "demo/files/demo-contract-renewal.pdf",
                        "DEMO_CONTRACT_RENEWAL"
                ),
                storedFile(
                        STAY_RECEIPT_FILE_ID,
                        tasks,
                        20,
                        "demo-stay-extension-receipt.pdf",
                        "demo/files/demo-stay-extension-receipt.pdf",
                        "DEMO_STAY_EXTENSION_RECEIPT"
                ),
                storedFile(
                        STAY_RESULT_FILE_ID,
                        tasks,
                        20,
                        "demo-stay-extension-result.pdf",
                        "demo/files/demo-stay-extension-result.pdf",
                        "DEMO_STAY_EXTENSION_RESULT"
                )
        );
    }

    private static StoredFileSeed storedFile(
            UUID storedFileId,
            List<TaskSeed> tasks,
            int taskNumber,
            String name,
            String resourcePath,
            String purpose
    ) {
        TaskSeed task = tasks.get(taskNumber - 1);
        return new StoredFileSeed(
                storedFileId,
                name,
                "application/pdf",
                purpose,
                task.taskId(),
                task.workerId(),
                storedFileId.toString(),
                resourcePath
        );
    }

    private static UUID demoUuid(int number) {
        return UUID.fromString(
                "94800000-0000-0000-0000-000000000%03d".formatted(number)
        );
    }
}
