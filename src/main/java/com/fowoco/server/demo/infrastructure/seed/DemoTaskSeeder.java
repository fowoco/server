package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.application.TaskContentCodec.EncodedTaskContent;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class DemoTaskSeeder {

    private final TaskRepository taskRepository;
    private final TaskContentCodec taskContentCodec;

    DemoTaskSeeder(TaskRepository taskRepository, TaskContentCodec taskContentCodec) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.taskContentCodec = Objects.requireNonNull(taskContentCodec, "taskContentCodec must not be null");
    }

    void seed(TaskSeed seed, DemoOperationalSeedContext context) {
        LocalDate dueDate = context.today().plusDays(seed.dueDays());
        EncodedTaskContent content = encode(seed, dueDate);
        Optional<Task> existing = taskRepository.findByIdAndCompanyId(
                seed.taskId(),
                context.companyId()
        );
        if (existing.isPresent()) {
            verifyExisting(existing.get(), seed, context);
            return;
        }
        Instant createdAt = context.now().minus(seed.createdDaysAgo(), ChronoUnit.DAYS);
        Instant desiredUpdatedAt = seed.status() == TaskStatus.COMPLETED
                ? context.now()
                : context.now().minus(Math.max(seed.createdDaysAgo() - 1, 0), ChronoUnit.DAYS);
        Instant updatedAt = desiredUpdatedAt.isBefore(createdAt) ? createdAt : desiredUpdatedAt;
        taskRepository.save(new Task(
                seed.taskId(),
                context.companyId(),
                seed.workerId(),
                seed.caseId(),
                seed.taskType(),
                seed.workflowId(),
                DemoOperationalSeedCatalog.WORKFLOW_CATALOG_VERSION,
                seed.title(),
                seed.description(),
                content.businessDataJson(),
                content.criticalFingerprint(),
                0L,
                seed.source(),
                seed.status(),
                dueDate,
                context.actorId(),
                context.actorId(),
                createdAt,
                updatedAt,
                0L
        ));
    }

    void verifyExisting(Task task, TaskSeed seed, DemoOperationalSeedContext context) {
        LocalDate dueDate = task.dueDate();
        EncodedTaskContent content = encode(seed, dueDate);
        if (!seed.taskId().equals(task.taskId())
                || !context.companyId().equals(task.companyId())
                || !seed.workerId().equals(task.workerId())
                || !seed.caseId().equals(task.caseId())
                || seed.taskType() != task.taskType()
                || !seed.workflowId().equals(task.workflowId())
                || !DemoOperationalSeedCatalog.WORKFLOW_CATALOG_VERSION.equals(task.workflowCatalogVersion())
                || !seed.title().equals(task.title())
                || !seed.description().equals(task.description())
                || !content.businessDataJson().equals(task.businessDataJson())
                || !content.criticalFingerprint().equals(task.criticalFingerprint())
                || seed.source() != task.source()
                || seed.status() != task.status()
                || task.contentRevision() != 0L
                || !context.actorId().equals(task.createdBy())
                || !context.actorId().equals(task.updatedBy())) {
            throw new IllegalStateException(
                    "a reserved demo task id already belongs to different task data"
            );
        }
    }

    private EncodedTaskContent encode(TaskSeed seed, LocalDate dueDate) {
        Map<String, Object> businessData = new LinkedHashMap<>(seed.businessData());
        businessData.put("worker_id", seed.workerId().toString());
        businessData.put("due_at", dueDate.toString());
        return taskContentCodec.encode(
                seed.workerId(),
                seed.workflowId(),
                seed.taskType().name(),
                seed.title(),
                seed.description(),
                dueDate,
                businessData
        );
    }
}
