package com.fowoco.server.workerlink.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class JpaWorkerResponseRepositoryTest {

    @Test
    void identifiesOnlyTheUploadFileUniqueConstraint() {
        RuntimeException uploadAlreadyLinked = new RuntimeException(new SQLException(
                "duplicate key violates constraint uq_worker_response_upload_file_company",
                "23505"
        ));
        RuntimeException unrelatedUniqueViolation = new RuntimeException(new SQLException(
                "duplicate key violates constraint uq_worker_response_idempotency",
                "23505"
        ));
        RuntimeException uploadForeignKeyViolation = new RuntimeException(new SQLException(
                "violates constraint uq_worker_response_upload_file_company",
                "23503"
        ));

        assertThat(JpaWorkerResponseRepository.isUniqueUploadFileViolation(uploadAlreadyLinked))
                .isTrue();
        assertThat(JpaWorkerResponseRepository.isUniqueUploadFileViolation(unrelatedUniqueViolation))
                .isFalse();
        assertThat(JpaWorkerResponseRepository.isUniqueUploadFileViolation(uploadForeignKeyViolation))
                .isFalse();
    }
}
