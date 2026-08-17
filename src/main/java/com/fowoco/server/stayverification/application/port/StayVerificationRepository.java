package com.fowoco.server.stayverification.application.port;

import com.fowoco.server.stayverification.application.StayVerificationCommand;
import com.fowoco.server.stayverification.domain.StayVerificationCase;
import com.fowoco.server.stayverification.domain.StayVerificationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StayVerificationRepository {

    List<ExpiredWorker> findExpiredWorkers(LocalDate today);

    List<ExpiredWorker> findExpiredWorkers(UUID companyId, LocalDate today);

    boolean insertIfAbsent(UUID verificationId, ExpiredWorker worker, Instant now);

    List<StayVerificationCase> findAll(UUID companyId, StayVerificationStatus status);

    Optional<StayVerificationCase> findById(UUID verificationId, UUID companyId);

    boolean update(StayVerificationCommand command, UUID companyId, Instant checkedAt, Instant now);

    record ExpiredWorker(
            UUID companyId,
            UUID workerId,
            String displayName,
            LocalDate stayExpiryDate
    ) {
    }
}
