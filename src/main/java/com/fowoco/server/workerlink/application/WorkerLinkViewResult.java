package com.fowoco.server.workerlink.application;

import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.time.LocalDate;
import java.util.List;

public record WorkerLinkViewResult(
        String guidance,
        LocalDate dueDate,
        List<WorkerResponseType> allowedResponses
) {
}
