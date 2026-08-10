package com.fowoco.server.task.infrastructure.persistence;

import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public TaskPage findAll(TaskSearchCriteria criteria) {
        Page<TaskJpaEntity> page = repository.search(
                criteria.companyId(),
                criteria.status(),
                criteria.taskType(),
                criteria.targetType(),
                criteria.source(),
                criteria.workerId(),
                criteria.caseId(),
                criteria.dueFrom(),
                criteria.dueTo(),
                normalizeKeyword(criteria.keyword()),
                PageRequest.of(
                        criteria.page(),
                        criteria.size(),
                        Sort.by(Sort.Order.asc("dueDate"), Sort.Order.desc("createdAt"))
                )
        );
        return new TaskPage(
                page.getContent().stream().map(TaskJpaEntity::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Override
    public List<Task> findOpenTasks(UUID companyId, int limit) {
        Page<TaskJpaEntity> page = repository.findOpenTasksByCompanyId(
                companyId,
                PageRequest.of(0, limit, Sort.by(Sort.Order.asc("dueDate"), Sort.Order.desc("createdAt")))
        );
        return page.getContent().stream().map(TaskJpaEntity::toDomain).toList();
    }

    @Override
    public long countByCompanyIdAndStatus(UUID companyId, TaskStatus status) {
        return repository.countByCompanyIdAndStatus(companyId, status);
    }

    @Override
    public long countOpenTasksDueOn(UUID companyId, LocalDate dueDate) {
        return repository.countOpenTasksDueOn(companyId, dueDate);
    }

    @Override
    public long countOpenTasksByCompanyId(UUID companyId) {
        return repository.countOpenTasksByCompanyId(companyId);
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

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
