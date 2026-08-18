package com.fowoco.server.stayverification.infrastructure.persistence;

import com.fowoco.server.stayverification.application.port.ExpiredStayCandidateReader;
import com.fowoco.server.stayverification.application.port.StayVerificationRepository;
import com.fowoco.server.stayverification.application.port.StayVerificationRepository.ExpiredWorker;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "transaction-only",
        matchIfMissing = true
)
public class LocalExpiredStayCandidateReader implements ExpiredStayCandidateReader {

    private final StayVerificationRepository repository;

    public LocalExpiredStayCandidateReader(StayVerificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ExpiredWorker> findExpiredWorkers(LocalDate today) {
        return repository.findExpiredWorkers(today);
    }
}
