package com.fowoco.server.workerimport.application;

import java.util.List;

public record WorkerImportView(
        WorkerImportJobRecord job,
        List<WorkerImportRowRecord> rows,
        int page,
        int size
) {
}
