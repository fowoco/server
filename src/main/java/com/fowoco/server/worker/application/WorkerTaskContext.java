package com.fowoco.server.worker.application;

import java.time.LocalDate;
import java.util.UUID;

public record WorkerTaskContext(
        UUID workerId,
        String employmentStatus,
        LocalDate stayExpiryDate,
        LocalDate contractStartDate,
        LocalDate contractEndDate
) {

    public boolean canReceiveNewTask() {
        return !"TERMINATED".equals(employmentStatus);
    }
}
