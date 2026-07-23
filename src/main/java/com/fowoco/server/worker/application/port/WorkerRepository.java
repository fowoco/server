package com.fowoco.server.worker.application.port;

import com.fowoco.server.worker.domain.Worker;
import java.util.Optional;
import java.util.UUID;

public interface WorkerRepository {

    void insert(Worker worker);

    Optional<Worker> findByWorkerIdAndCompanyId(UUID workerId, UUID companyId);

    void update(Worker worker);
}
