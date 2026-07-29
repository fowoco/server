package com.fowoco.server.document.application;

import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.util.List;
import java.util.Map;
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

    public DocumentService(
            WorkerDocumentRepository workerDocumentRepository,
            WorkerRepository workerRepository
    ) {
        this.workerDocumentRepository = workerDocumentRepository;
        this.workerRepository = workerRepository;
    }

    @Transactional(readOnly = true)
    public DocumentPageResult findPage(UUID companyId, WorkerDocumentSearchQuery query) {
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
