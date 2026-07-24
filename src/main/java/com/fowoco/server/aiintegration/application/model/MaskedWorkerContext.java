package com.fowoco.server.aiintegration.application.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimum worker context allowed to cross the AI boundary.
 *
 * <p>Legal name, phone, passport number, residence number, account number, and document contents
 * must never be added here.</p>
 */
public record MaskedWorkerContext(
        UUID workerRef,
        String preferredLanguage,
        String workStatus,
        LocalDate stayExpiryDate
) {

    public MaskedWorkerContext {
        Objects.requireNonNull(workerRef, "workerRef must not be null");
        Objects.requireNonNull(preferredLanguage, "preferredLanguage must not be null");
        Objects.requireNonNull(workStatus, "workStatus must not be null");
    }
}
