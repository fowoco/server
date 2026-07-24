package com.fowoco.server.worker.application.port;

import com.fowoco.server.worker.application.WorkerSearchQuery;
import com.fowoco.server.worker.domain.Worker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerRepository {

    void insert(Worker worker);

    Optional<Worker> findByWorkerIdAndCompanyId(UUID workerId, UUID companyId);

    Worker update(Worker worker);

    List<Worker> findPage(UUID companyId, WorkerSearchQuery query);

    long countPage(UUID companyId, WorkerSearchQuery query);
}
