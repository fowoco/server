package com.fowoco.server.stayverification.application.port;

import com.fowoco.server.stayverification.application.port.StayVerificationRepository.ExpiredWorker;
import java.time.LocalDate;
import java.util.List;

public interface ExpiredStayCandidateReader {

    List<ExpiredWorker> findExpiredWorkers(LocalDate today);
}
