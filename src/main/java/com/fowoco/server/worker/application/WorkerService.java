package com.fowoco.server.worker.application;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.worker.application.error.WorkerErrorCode;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import java.time.Clock;
import java.util.UUID;
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

    @Transactional(readOnly = true)
    public Worker findDetail(UUID workerId, UUID companyId) {
        return workerRepository.findByWorkerIdAndCompanyId(workerId, companyId)
                .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_NOT_FOUND));
    }

    @Transactional
    public Worker patch(WorkerPatchCommand command) {
        Worker existing = findDetail(command.workerId(), command.companyId());
        if (existing.version() != command.expectedVersion()) {
            throw new ApiException(WorkerErrorCode.WORKER_VERSION_CONFLICT);
        }

        Worker updated = new Worker(
                existing.workerId(),
                existing.companyId(),
                orElseKeep(command.displayName(), existing.displayName()),
                orElseKeep(command.nationalityCode(), existing.nationalityCode()),
                orElseKeep(command.preferredLanguage(), existing.preferredLanguage()),
                orElseKeep(command.workStatus(), existing.workStatus()),
                orElseKeep(command.visaExpiryDate(), existing.visaExpiryDate()),
                orElseKeep(command.contractStartDate(), existing.contractStartDate()),
                orElseKeep(command.contractEndDate(), existing.contractEndDate()),
                existing.createdAt(),
                clock.instant(),
                existing.version()
        );

        return workerRepository.update(updated);
    }

    private static <T> T orElseKeep(T newValue, T existingValue) {
        return newValue != null ? newValue : existingValue;
    }
}
