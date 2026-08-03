package com.fowoco.server.aiintegration.application.model;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-managed Worker context for the current fake-data demo.
 *
 * <p>The HTTP Adapter sends only {@code workerRef} and {@code requestedFields}. The remaining
 * fields stay inside Server for response validation. Service credentials, JWTs, passwords, and
 * Worker Link tokens must never be placed in {@code requestedFields}.</p>
 */
public record WorkerContext(
        UUID workerRef,
        String displayName,
        String nationalityCode,
        String preferredLanguage,
        String workStatus,
        LocalDate stayExpiryDate,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        Map<String, String> requestedFields
) {

    public WorkerContext {
        Objects.requireNonNull(workerRef, "workerRef must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(workStatus, "workStatus must not be null");
        Objects.requireNonNull(requestedFields, "requestedFields must not be null");
        requestedFields = Map.copyOf(requestedFields);
    }
}
