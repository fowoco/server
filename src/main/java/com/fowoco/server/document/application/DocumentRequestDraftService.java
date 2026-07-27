package com.fowoco.server.document.application;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.document.application.error.DocumentErrorCode;
import com.fowoco.server.document.application.port.DocumentRequestDraftRepository;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentRequestDraftService {

    private final TaskRepository taskRepository;
    private final DocumentRequestDraftRepository documentRequestDraftRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public DocumentRequestDraftService(
            TaskRepository taskRepository,
            DocumentRequestDraftRepository documentRequestDraftRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.documentRequestDraftRepository = documentRequestDraftRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public DocumentRequestDraft upsert(DocumentRequestDraftCommand command) {
        taskRepository.findByIdAndCompanyId(command.taskId(), command.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        Optional<DocumentRequestDraft> existing = documentRequestDraftRepository
                .findByTaskIdAndCompanyId(command.taskId(), command.companyId());

        if (existing.isEmpty()) {
            DocumentRequestDraft draft = DocumentRequestDraft.create(
                    uuidGenerator.generate(),
                    command.taskId(),
                    command.companyId(),
                    command.language(),
                    command.documentTypes(),
                    command.message(),
                    clock.instant()
            );
            documentRequestDraftRepository.insert(draft);
            return draft;
        }

        DocumentRequestDraft current = existing.get();
        if (current.version() != command.expectedVersion()) {
            throw new ApiException(DocumentErrorCode.DOCUMENT_REQUEST_DRAFT_VERSION_CONFLICT);
        }
        DocumentRequestDraft updated = current.withUpdatedContent(
                command.language(),
                command.documentTypes(),
                command.message(),
                clock.instant()
        );
        return documentRequestDraftRepository.update(updated);
    }
}
