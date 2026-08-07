package com.fowoco.server.worker.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.worker.application.error.WorkerErrorCode;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerService(
            WorkerRepository workerRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerRepository = workerRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public Worker register(WorkerCreateCommand command, ActorContext actor) {
        bindTenant(actor);
        Worker worker = Worker.create(
                uuidGenerator.generate(),
                actor.companyId(),
                command.displayName(),
                command.nationalityCode(),
                command.preferredLanguage(),
                command.visaType(),
                command.stayExpiryDate(),
                command.contractStartDate(),
                command.contractEndDate(),
                command.employmentPermitEndDate(),
                command.employmentActivityEndDate(),
                DatabaseTimestamp.now(clock)
        );
        workerRepository.insert(worker);
        return worker;
    }

    @Transactional(readOnly = true)
    public Worker findDetail(UUID workerId, ActorContext actor) {
        bindTenant(actor);
        return workerRepository.findByWorkerIdAndCompanyId(workerId, actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public WorkerPageResult findPage(ActorContext actor, WorkerSearchQuery query) {
        bindTenant(actor);
        List<Worker> items = workerRepository.findPage(actor.companyId(), query);
        long totalElements = workerRepository.countPage(actor.companyId(), query);
        return new WorkerPageResult(items, query.page(), query.size(), totalElements);
    }

    @Transactional
    public Worker patch(WorkerPatchCommand command, ActorContext actor) {
        bindTenant(actor);
        Worker existing = findDetail(command.workerId(), actor);
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
                orElseKeep(command.visaType(), existing.visaType()),
                orElseKeep(command.stayExpiryDate(), existing.stayExpiryDate()),
                orElseKeep(command.contractStartDate(), existing.contractStartDate()),
                orElseKeep(command.contractEndDate(), existing.contractEndDate()),
                orElseKeep(command.employmentPermitEndDate(), existing.employmentPermitEndDate()),
                orElseKeep(command.employmentActivityEndDate(), existing.employmentActivityEndDate()),
                existing.createdAt(),
                DatabaseTimestamp.nowNotBefore(clock, existing.createdAt()),
                existing.version()
        );

        return workerRepository.update(updated);
    }

    private static <T> T orElseKeep(T newValue, T existingValue) {
        return newValue != null ? newValue : existingValue;
    }

    private void bindTenant(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
    }
}
