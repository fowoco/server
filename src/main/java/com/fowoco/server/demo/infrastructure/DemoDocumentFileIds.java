package com.fowoco.server.demo.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable identifiers for file rows materialized from the operational demo document catalog. */
public final class DemoDocumentFileIds {

    private static final String OPERATIONAL_DOCUMENT_PREFIX =
            "95000000-0000-0000-0000-000000000";
    private static final String MATERIALIZED_FILE_NAMESPACE =
            "fowoco-demo-operational-document-file-v1:";

    private DemoDocumentFileIds() {
    }

    public static UUID materializedFileId(UUID workerDocumentId) {
        if (!isOperationalDemoDocumentId(workerDocumentId)) {
            throw new IllegalArgumentException("not an operational Demo Company document id");
        }
        return UUID.nameUUIDFromBytes(
                (MATERIALIZED_FILE_NAMESPACE + workerDocumentId)
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    public static boolean isOperationalDemoDocumentId(UUID workerDocumentId) {
        String value = workerDocumentId.toString();
        if (!value.startsWith(OPERATIONAL_DOCUMENT_PREFIX)) {
            return false;
        }
        try {
            int sequence = Integer.parseInt(value.substring(value.length() - 3));
            return sequence >= 1 && sequence <= 84 && sequence != 18;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
