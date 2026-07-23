package com.fowoco.server.worker.application;

import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerService(
            WorkerRepository workerRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerRepository = workerRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public Worker register(WorkerCreateCommand command) {
        Worker worker = Worker.create(
                uuidGenerator.generate(),
                command.companyId(),
                command.displayName(),
                command.nationalityCode(),
                command.preferredLanguage(),
                command.visaExpiryDate(),
                command.contractStartDate(),
                command.contractEndDate(),
                clock.instant()
        );
        workerRepository.insert(worker);
        return worker;
    }
}
