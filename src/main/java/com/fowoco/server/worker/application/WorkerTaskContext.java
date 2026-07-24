package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.WorkerStatus;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerTaskContext(
        UUID workerId,
        WorkerStatus workStatus,
        LocalDate stayExpiryDate,
        LocalDate contractStartDate,
        LocalDate contractEndDate
) {

    public boolean canReceiveNewTask() {
        return workStatus.isCurrentlyEmployed();
    }
}
