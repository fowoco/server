package com.fowoco.server.task.infrastructure.persistence;

import com.fowoco.server.task.application.port.TaskChecklistRepository;
import com.fowoco.server.task.domain.TaskChecklistItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTaskChecklistRepository implements TaskChecklistRepository {

    private final SpringDataTaskChecklistJpaRepository repository;

    public JpaTaskChecklistRepository(SpringDataTaskChecklistJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TaskChecklistItem> findAllByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
        return repository.findAllByTaskIdAndCompanyIdOrderByCreatedAtAsc(taskId, companyId)
                .stream()
                .map(TaskChecklistItemJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<TaskChecklistItem> findByIdAndTaskIdAndCompanyId(
            UUID checklistItemId,
            UUID taskId,
            UUID companyId
    ) {
        return repository.findByChecklistItemIdAndTaskIdAndCompanyId(
                        checklistItemId,
                        taskId,
                        companyId
                )
                .map(TaskChecklistItemJpaEntity::toDomain);
    }

    @Override
    public List<TaskChecklistItem> saveAll(List<TaskChecklistItem> items) {
        return repository.saveAllAndFlush(
                        items.stream().map(TaskChecklistItemJpaEntity::new).toList()
                )
                .stream()
                .map(TaskChecklistItemJpaEntity::toDomain)
                .toList();
    }

    @Override
    public TaskChecklistItem save(TaskChecklistItem item) {
        TaskChecklistItemJpaEntity entity = repository
                .findByChecklistItemIdAndTaskIdAndCompanyId(
                        item.checklistItemId(),
                        item.taskId(),
                        item.companyId()
                )
                .map(existing -> {
                    existing.apply(item);
                    return existing;
                })
                .orElseGet(() -> new TaskChecklistItemJpaEntity(item));
        return repository.saveAndFlush(entity).toDomain();
    }
}
