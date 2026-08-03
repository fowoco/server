package com.fowoco.server.aiintegration.application.model;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Worker data sent to the AI Runtime for the current fake-data demo.
 *
 * <p>{@code requestedFields} carries the original values requested by the Agent. Service
 * credentials, JWTs, passwords, and Worker Link tokens must never be placed in this map.</p>
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
