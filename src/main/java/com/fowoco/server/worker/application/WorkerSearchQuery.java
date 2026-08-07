package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.WorkerStatus;
import java.time.LocalDate;

public final class WorkerSearchQuery {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final WorkerStatus status;
    private final String language;
    private final LocalDate stayExpiryBefore;
    private final LocalDate contractEndBefore;
    private final LocalDate employmentPermitEndBefore;
    private final LocalDate employmentActivityEndBefore;
    private final int page;
    private final int size;

    public WorkerSearchQuery(
            WorkerStatus status,
            String language,
            LocalDate stayExpiryBefore,
            LocalDate contractEndBefore,
            LocalDate employmentPermitEndBefore,
            LocalDate employmentActivityEndBefore,
            Integer page,
            Integer size
    ) {
        this.status = status;
        this.language = language;
        this.stayExpiryBefore = stayExpiryBefore;
        this.contractEndBefore = contractEndBefore;
        this.employmentPermitEndBefore = employmentPermitEndBefore;
        this.employmentActivityEndBefore = employmentActivityEndBefore;
        this.page = page == null ? 0 : page;
        this.size = size == null ? 20 : size;
        if (this.page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (this.size < MIN_SIZE || this.size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between " + MIN_SIZE + " and " + MAX_SIZE);
        }
    }

    public WorkerStatus status() {
        return status;
    }

    public String language() {
        return language;
    }

    public LocalDate stayExpiryBefore() {
        return stayExpiryBefore;
    }

    public LocalDate contractEndBefore() {
        return contractEndBefore;
    }

    public LocalDate employmentPermitEndBefore() {
        return employmentPermitEndBefore;
    }

    public LocalDate employmentActivityEndBefore() {
        return employmentActivityEndBefore;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }
}
