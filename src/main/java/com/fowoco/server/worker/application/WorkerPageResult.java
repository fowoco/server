package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.Worker;
import java.util.List;

public record WorkerPageResult(List<Worker> items, int page, int size, long totalElements) {
}
