package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ChecklistSeed;
import com.fowoco.server.task.application.port.TaskChecklistRepository;
import com.fowoco.server.task.domain.TaskChecklistItem;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

final class DemoTaskChecklistSeeder {

    private final TaskChecklistRepository checklistRepository;

    DemoTaskChecklistSeeder(TaskChecklistRepository checklistRepository) {
        this.checklistRepository = Objects.requireNonNull(
                checklistRepository,
                "checklistRepository must not be null"
        );
    }

    void seed(ChecklistSeed seed, DemoOperationalSeedContext context) {
        Optional<TaskChecklistItem> existing = checklistRepository.findByIdAndTaskIdAndCompanyId(
                seed.checklistItemId(),
                seed.taskId(),
                context.companyId()
        );
        if (existing.isPresent()) {
            verifyExisting(existing.get(), seed, context);
            return;
        }
        Instant createdAt = context.now().minus(seed.createdHoursAgo(), ChronoUnit.HOURS);
        Instant completedAt = seed.completed()
                ? context.now().minus(seed.completedHoursAgo(), ChronoUnit.HOURS)
                : null;
        checklistRepository.save(new TaskChecklistItem(
                seed.checklistItemId(),
                seed.taskId(),
                context.companyId(),
                seed.itemCode(),
                seed.label(),
                seed.required(),
                seed.completed(),
                seed.completed() ? context.actorId() : null,
                completedAt,
                createdAt,
                completedAt == null ? createdAt : completedAt,
                0L
        ));
    }

    void verifyExisting(
            TaskChecklistItem item,
            ChecklistSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!seed.checklistItemId().equals(item.checklistItemId())
                || !seed.taskId().equals(item.taskId())
                || !context.companyId().equals(item.companyId())
                || !seed.itemCode().equals(item.itemCode())
                || !seed.label().equals(item.label())
                || seed.required() != item.required()
                || seed.completed() != item.completed()
                || (seed.completed() && !context.actorId().equals(item.completedBy()))
                || (!seed.completed() && item.completedBy() != null)) {
            throw new IllegalStateException(
                    "a reserved demo checklist item id already belongs to different checklist data"
            );
        }
    }
}
