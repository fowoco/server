package com.fowoco.server.document.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.document.application.error.DocumentErrorCode;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final WorkerDocumentRepository workerDocumentRepository;
    private final WorkerRepository workerRepository;
    private final StoredFileRepository storedFileRepository;
    private final TenantDatabaseContext tenantDatabaseContext;

    public DocumentService(
            WorkerDocumentRepository workerDocumentRepository,
            WorkerRepository workerRepository,
            StoredFileRepository storedFileRepository,
            TenantDatabaseContext tenantDatabaseContext
    ) {
        this.workerDocumentRepository = workerDocumentRepository;
        this.workerRepository = workerRepository;
        this.storedFileRepository = storedFileRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
    }

    @Transactional(readOnly = true)
    public DocumentDetailResult findById(UUID workerDocumentId, ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        UUID companyId = actor.companyId();
        WorkerDocument document = workerDocumentRepository
                .findByIdAndCompanyId(workerDocumentId, companyId)
                .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        String displayName = workerRepository
                .findAllByWorkerIdsAndCompanyId(Set.of(document.workerId()), companyId)
                .stream()
                .findFirst()
                .map(Worker::displayName)
                .orElse(null);
        StoredFile storedFile = document.fileId() == null
                ? null
                : storedFileRepository.findByIdAndCompanyId(document.fileId(), companyId).orElse(null);
        return new DocumentDetailResult(document, displayName, storedFile);
    }

    @Transactional(readOnly = true)
    public DocumentPageResult findPage(ActorContext actor, WorkerDocumentSearchQuery query) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        UUID companyId = actor.companyId();
        List<WorkerDocument> items = workerDocumentRepository.findPage(companyId, query);
        long totalElements = workerDocumentRepository.countPage(companyId, query);

        Set<UUID> workerIds = items.stream()
                .map(WorkerDocument::workerId)
                .collect(Collectors.toSet());
        Map<UUID, String> workerDisplayNames = workerRepository
                .findAllByWorkerIdsAndCompanyId(workerIds, companyId)
                .stream()
                .collect(Collectors.toMap(Worker::workerId, Worker::displayName));

        return new DocumentPageResult(items, workerDisplayNames, query.page(), query.size(), totalElements);
    }
}
