package com.fowoco.server.document.application;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.document.domain.ChecklistItemDocumentMapper;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskChecklistRepository;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskChecklistItem;
import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentReadinessService {

    private final TaskRepository taskRepository;
    private final TaskChecklistRepository taskChecklistRepository;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final Clock clock;

    public DocumentReadinessService(
            TaskRepository taskRepository,
            TaskChecklistRepository taskChecklistRepository,
            WorkerDocumentRepository workerDocumentRepository,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.taskChecklistRepository = taskChecklistRepository;
        this.workerDocumentRepository = workerDocumentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DocumentReadinessResult calculate(UUID taskId, UUID companyId) {
        Task task = taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        Set<DocumentType> requiredTypes = requiredDocumentTypes(taskId, companyId);

        LocalDate today = LocalDate.now(clock);
        WorkerDocumentSearchQuery allDocumentsQuery = new WorkerDocumentSearchQuery(
                task.workerId(), null, null, null, 0, 100
        );
        List<WorkerDocument> workerDocuments = workerDocumentRepository.findPage(companyId, allDocumentsQuery);

        Set<DocumentType> available = EnumSet.noneOf(DocumentType.class);
        Set<DocumentType> expired = EnumSet.noneOf(DocumentType.class);
        for (WorkerDocument document : workerDocuments) {
            if (!requiredTypes.contains(document.documentType())) {
                continue;
            }
            boolean isExpired = document.expiryDate() != null && document.expiryDate().isBefore(today);
            if (isExpired) {
                expired.add(document.documentType());
            } else {
                available.add(document.documentType());
            }
        }

        Set<DocumentType> missing = EnumSet.copyOf(requiredTypes);
        missing.removeAll(available);
        missing.removeAll(expired);

        boolean completionBlocked = !missing.isEmpty() || !expired.isEmpty();

        return new DocumentReadinessResult(
                List.copyOf(requiredTypes),
                List.copyOf(available),
                List.copyOf(missing),
                List.copyOf(expired),
                completionBlocked
        );
    }

    private Set<DocumentType> requiredDocumentTypes(UUID taskId, UUID companyId) {
        List<TaskChecklistItem> checklistItems =
                taskChecklistRepository.findAllByTaskIdAndCompanyId(taskId, companyId);
        Set<DocumentType> requiredTypes = EnumSet.noneOf(DocumentType.class);
        for (TaskChecklistItem item : checklistItems) {
            if (!item.required()) {
                continue;
            }
            ChecklistItemDocumentMapper.toDocumentType(item.itemCode())
                    .ifPresent(requiredTypes::add);
        }
        return requiredTypes;
    }
}
