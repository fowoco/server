package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentSeed;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

final class DemoWorkerDocumentSeeder {

    private final WorkerDocumentRepository workerDocumentRepository;

    DemoWorkerDocumentSeeder(WorkerDocumentRepository workerDocumentRepository) {
        this.workerDocumentRepository = Objects.requireNonNull(
                workerDocumentRepository,
                "workerDocumentRepository must not be null"
        );
    }

    void seed(DocumentSeed seed, DemoOperationalSeedContext context) {
        LocalDate expiryDate = seed.expiryDays() == null
                ? null
                : context.today().plusDays(seed.expiryDays());
        Optional<WorkerDocument> existing =
                workerDocumentRepository.findByIdAndWorkerIdAndCompanyId(
                        seed.documentId(),
                        seed.workerId(),
                        context.companyId()
                );
        if (existing.isPresent()) {
            verifyExisting(existing.get(), seed, context);
            return;
        }
        workerDocumentRepository.insert(new WorkerDocument(
                seed.documentId(),
                seed.workerId(),
                context.companyId(),
                null,
                seed.documentType(),
                seed.submissionStatus(),
                expiryDate,
                seed.destination(),
                seed.note(),
                seed.fileId(),
                context.now(),
                context.now(),
                0L
        ));
    }

    void verifyExisting(
            WorkerDocument document,
            DocumentSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!seed.documentId().equals(document.workerDocumentId())
                || !seed.workerId().equals(document.workerId())
                || !context.companyId().equals(document.companyId())
                || seed.documentType() != document.documentType()
                || seed.submissionStatus() != document.submissionStatus()
                || !Objects.equals(seed.destination(), document.destination())
                || !Objects.equals(seed.note(), document.note())
                || !Objects.equals(seed.fileId(), document.fileId())) {
            throw new IllegalStateException(
                    "a reserved demo worker document id already belongs to different document data"
            );
        }
    }
}
