package com.fowoco.server.task.infrastructure.persistence;

import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTaskRepository implements TaskRepository {

    private final SpringDataTaskJpaRepository repository;

    public JpaTaskRepository(SpringDataTaskJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Task> findByIdAndCompanyId(UUID taskId, UUID companyId) {
        return repository.findByTaskIdAndCompanyId(taskId, companyId).map(TaskJpaEntity::toDomain);
    }

    @Override
    public Task save(Task task) {
        TaskJpaEntity entity = repository.findByTaskIdAndCompanyId(task.taskId(), task.companyId())
                .map(existing -> {
                    existing.apply(task);
                    return existing;
                })
                .orElseGet(() -> new TaskJpaEntity(task));
        return repository.saveAndFlush(entity).toDomain();
    }
}
