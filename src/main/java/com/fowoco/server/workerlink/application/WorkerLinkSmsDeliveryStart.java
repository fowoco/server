package com.fowoco.server.workerlink.application;

import java.util.Objects;

public record WorkerLinkSmsDeliveryStart(
        boolean sendRequired,
        WorkerLinkDeliveryResult result
) {
    public WorkerLinkSmsDeliveryStart {
        Objects.requireNonNull(result, "result must not be null");
    }

    public static WorkerLinkSmsDeliveryStart ready(WorkerLinkDeliveryResult result) {
        return new WorkerLinkSmsDeliveryStart(true, result);
    }

    public static WorkerLinkSmsDeliveryStart alreadySent(WorkerLinkDeliveryResult result) {
        return new WorkerLinkSmsDeliveryStart(false, result);
    }
}
