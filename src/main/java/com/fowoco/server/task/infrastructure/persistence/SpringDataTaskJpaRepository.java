package com.fowoco.server.task.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskTargetType;
import com.fowoco.server.task.domain.TaskType;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTaskJpaRepository extends JpaRepository<TaskJpaEntity, UUID> {

    Optional<TaskJpaEntity> findByTaskIdAndCompanyId(UUID taskId, UUID companyId);

    @Query("""
            SELECT task
              FROM TaskJpaEntity task
             WHERE task.companyId = :companyId
               AND (:status IS NULL OR task.status = :status)
               AND (:taskType IS NULL OR task.taskType = :taskType)
               AND (:targetType IS NULL OR task.targetType = :targetType)
               AND (:source IS NULL OR task.source = :source)
               AND (:workerId IS NULL OR task.workerId = :workerId)
               AND (:caseId IS NULL OR task.caseId = :caseId)
               AND (:dueFrom IS NULL OR task.dueDate >= :dueFrom)
               AND (:dueTo IS NULL OR task.dueDate <= :dueTo)
               AND (
                    :keyword IS NULL
                    OR LOWER(task.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                    OR LOWER(COALESCE(task.description, ''))
                        LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
               )
            """)
    Page<TaskJpaEntity> search(
            @Param("companyId") UUID companyId,
            @Param("status") TaskStatus status,
            @Param("taskType") TaskType taskType,
            @Param("targetType") TaskTargetType targetType,
            @Param("source") TaskSource source,
            @Param("workerId") UUID workerId,
            @Param("caseId") UUID caseId,
            @Param("dueFrom") LocalDate dueFrom,
            @Param("dueTo") LocalDate dueTo,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT task
              FROM TaskJpaEntity task
             WHERE task.companyId = :companyId
               AND task.status <> com.fowoco.server.task.domain.TaskStatus.COMPLETED
               AND task.status <> com.fowoco.server.task.domain.TaskStatus.CANCELLED
            """)
    Page<TaskJpaEntity> findOpenTasksByCompanyId(
            @Param("companyId") UUID companyId,
            Pageable pageable
    );

    long countByCompanyIdAndStatus(UUID companyId, TaskStatus status);

    @Query("""
            SELECT COUNT(task)
              FROM TaskJpaEntity task
             WHERE task.companyId = :companyId
               AND task.dueDate = :dueDate
               AND task.status <> com.fowoco.server.task.domain.TaskStatus.COMPLETED
               AND task.status <> com.fowoco.server.task.domain.TaskStatus.CANCELLED
            """)
    long countOpenTasksDueOn(@Param("companyId") UUID companyId, @Param("dueDate") LocalDate dueDate);
}
