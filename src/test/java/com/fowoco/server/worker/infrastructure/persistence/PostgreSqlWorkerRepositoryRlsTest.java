package com.fowoco.server.worker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fowoco.server.common.database.PostgreSqlAccessDeniedClassification;
import com.fowoco.server.common.database.PostgreSqlAccessDeniedClassifier;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.worker.application.WorkerSearchQuery;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TransactionRequiredException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgreSqlWorkerRepositoryRlsTest {

    private PostgreSqlWorkerDataFixture dataFixture;
    private PostgreSqlWorkerRestrictedRuntimeFixture runtimeFixture;
    private WorkerRepository workerRepository;
    private EntityManager entityManager;
    private TransactionTemplate transactionTemplate;
    private TenantDatabaseContext tenantDatabaseContext;
    private PostgreSqlAccessDeniedClassifier accessDeniedClassifier;

    @BeforeAll
    void setUpRestrictedRepositoryFixture() throws Exception {
        dataFixture = new PostgreSqlWorkerDataFixture();
        runtimeFixture = PostgreSqlWorkerRestrictedRuntimeFixture.startFromEnvironment(
                dataFixture
        );
        workerRepository = runtimeFixture.bean(WorkerRepository.class);
        entityManager = runtimeFixture.bean(EntityManager.class);
        transactionTemplate = new TransactionTemplate(
                runtimeFixture.bean(PlatformTransactionManager.class)
        );
        tenantDatabaseContext = runtimeFixture.bean(TenantDatabaseContext.class);
        accessDeniedClassifier = runtimeFixture.bean(PostgreSqlAccessDeniedClassifier.class);
    }

    @AfterAll
    void tearDownRestrictedRepositoryFixture() throws Exception {
        PostgreSqlWorkerRestrictedRuntimeFixture fixtureToClose = runtimeFixture;
        if (fixtureToClose != null) {
            fixtureToClose.close();
            runtimeFixture = null;
        }
    }

    @Test
    void repositoryReadsWithTransactionWithoutTenantBindingFailClosed() {
        WorkerSearchQuery allWorkers = new WorkerSearchQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20
        );

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(workerRepository.findByWorkerIdAndCompanyId(
                    dataFixture.workerA(),
                    dataFixture.companyA()
            )).isEmpty();
            assertThat(workerRepository.findByWorkerIdAndCompanyId(
                    dataFixture.workerB(),
                    dataFixture.companyB()
            )).isEmpty();
            assertThat(workerRepository.findPage(dataFixture.companyA(), allWorkers))
                    .isEmpty();
            assertThat(workerRepository.countPage(dataFixture.companyA(), allWorkers))
                    .isZero();
            assertThat(workerRepository.findAllByWorkerIdsAndCompanyId(
                    Set.of(dataFixture.workerA(), dataFixture.workerB()),
                    dataFixture.companyA()
            )).isEmpty();
        });

        dataFixture.assertRowsUnchanged();
    }

    @Test
    void repositoryInsertOutsideTransactionFailsBeforeSqlAndDoesNotCreateRow() {
        Throwable failure = catchThrowable(() -> workerRepository.insert(
                dataFixture.newWorker(
                        dataFixture.outsideTransactionWorker(),
                        "Outside transaction"
                )
        ));

        assertThat(failure).isNotNull();
        assertThat(causeOfType(failure, TransactionRequiredException.class)).isNotNull();
        assertThat(accessDeniedClassifier.classify(failure).confirmed()).isFalse();
        dataFixture.assertRowsUnchanged();
    }

    @Test
    void repositoryInsertWithTransactionWithoutTenantBindingIsRejectedByRls() {
        ContextProbe boundProbe = transactionTemplate.execute(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(dataFixture.companyA());
            workerRepository.insert(dataFixture.newWorker(
                    dataFixture.boundRollbackWorker(),
                    "Bound rollback"
            ));
            assertThat(workerRepository.findByWorkerIdAndCompanyId(
                    dataFixture.boundRollbackWorker(),
                    dataFixture.companyA()
            )).isPresent();
            ContextProbe probe = currentContextProbe();
            status.setRollbackOnly();
            return probe;
        });
        assertThat(boundProbe).isNotNull();
        assertThat(boundProbe.companyId()).isEqualTo(dataFixture.companyA().toString());
        assertThat(dataFixture.candidateWorkerCount()).isZero();

        Throwable failure = catchThrowable(() -> transactionTemplate.executeWithoutResult(
                status -> workerRepository.insert(dataFixture.newWorker(
                        dataFixture.unboundTransactionWorker(),
                        "Unbound transaction"
                ))
        ));

        assertThat(failure).isInstanceOf(DataAccessException.class);
        PostgreSqlAccessDeniedClassification classification =
                accessDeniedClassifier.classify(failure);
        assertThat(classification.confirmed()).isTrue();
        assertThat(classification.sqlState()).isEqualTo("42501");

        ContextProbe clearedProbe = transactionTemplate.execute(
                status -> currentContextProbe()
        );
        assertThat(clearedProbe).isNotNull();
        assertThat(clearedProbe.backendPid()).isEqualTo(boundProbe.backendPid());
        assertThat(clearedProbe.companyId()).isNull();
        assertThat(clearedProbe.workerAVisible()).isFalse();
        assertThat(clearedProbe.workerBVisible()).isFalse();
        dataFixture.assertRowsUnchanged();
    }

    @Test
    void repositoryUpdateWithoutTransactionOrTenantBindingCannotModifyRows() {
        Worker forbiddenUpdate = new Worker(
                dataFixture.workerA(),
                dataFixture.companyA(),
                "Forbidden update",
                "PH",
                "en",
                WorkerStatus.ON_LEAVE,
                "E-9",
                LocalDate.of(2027, 12, 31),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31),
                null,
                null,
                dataFixture.fixtureTime(),
                dataFixture.fixtureTime().plusSeconds(3600),
                0L
        );

        Throwable outsideTransactionFailure = catchThrowable(
                () -> workerRepository.update(forbiddenUpdate)
        );
        assertWorkerNotFound(outsideTransactionFailure);
        assertThat(accessDeniedClassifier.classify(outsideTransactionFailure).confirmed())
                .isFalse();

        Throwable unboundTransactionFailure = catchThrowable(
                () -> transactionTemplate.executeWithoutResult(
                        status -> workerRepository.update(forbiddenUpdate)
                )
        );
        assertWorkerNotFound(unboundTransactionFailure);
        assertThat(accessDeniedClassifier.classify(unboundTransactionFailure).confirmed())
                .isFalse();
        dataFixture.assertRowsUnchanged();
    }

    @Test
    void testOnlyDeleteWithoutTenantBindingAffectsNoRows() {
        Integer deletedRows = transactionTemplate.execute(status ->
                entityManager.createNativeQuery(
                                "DELETE FROM public.worker WHERE worker_id = ?1"
                        )
                        .setParameter(1, dataFixture.workerA())
                        .executeUpdate()
        );

        assertThat(deletedRows).isZero();
        dataFixture.assertRowsUnchanged();
    }

    @Test
    void boundTransactionsSeeOnlyTheirTenantAndDoNotLeakContext() {
        ContextProbe companyAProbe = contextProbeForTenant(dataFixture.companyA());
        ContextProbe clearedAfterA = transactionTemplate.execute(
                status -> currentContextProbe()
        );
        ContextProbe companyBProbe = contextProbeForTenant(dataFixture.companyB());
        ContextProbe clearedAfterB = transactionTemplate.execute(
                status -> currentContextProbe()
        );

        assertThat(companyAProbe.companyId()).isEqualTo(dataFixture.companyA().toString());
        assertThat(companyAProbe.workerAVisible()).isTrue();
        assertThat(companyAProbe.workerBVisible()).isFalse();
        assertThat(companyBProbe.companyId()).isEqualTo(dataFixture.companyB().toString());
        assertThat(companyBProbe.workerAVisible()).isFalse();
        assertThat(companyBProbe.workerBVisible()).isTrue();

        assertCleared(clearedAfterA);
        assertCleared(clearedAfterB);
        assertThat(List.of(
                clearedAfterA.backendPid(),
                companyBProbe.backendPid(),
                clearedAfterB.backendPid()
        )).containsOnly(companyAProbe.backendPid());
        dataFixture.assertRowsUnchanged();
    }

    private ContextProbe contextProbeForTenant(UUID companyId) {
        ContextProbe probe = transactionTemplate.execute(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
            return currentContextProbe();
        });
        if (probe == null) {
            throw new IllegalStateException("Tenant context probe returned no result");
        }
        return probe;
    }

    private ContextProbe currentContextProbe() {
        return new ContextProbe(
                ((Number) entityManager.createNativeQuery(
                        "SELECT pg_catalog.pg_backend_pid()"
                ).getSingleResult()).intValue(),
                currentCompanyId(),
                workerRepository.findByWorkerIdAndCompanyId(
                        dataFixture.workerA(),
                        dataFixture.companyA()
                ).isPresent(),
                workerRepository.findByWorkerIdAndCompanyId(
                        dataFixture.workerB(),
                        dataFixture.companyB()
                ).isPresent()
        );
    }

    private String currentCompanyId() {
        Object value = entityManager.createNativeQuery(
                """
                SELECT NULLIF(
                    pg_catalog.current_setting('app.company_id', true),
                    ''
                )
                """
        ).getSingleResult();
        return value == null ? null : value.toString();
    }

    private void assertCleared(ContextProbe probe) {
        assertThat(probe).isNotNull();
        assertThat(probe.companyId()).isNull();
        assertThat(probe.workerAVisible()).isFalse();
        assertThat(probe.workerBVisible()).isFalse();
    }

    private void assertWorkerNotFound(Throwable failure) {
        IllegalStateException notFound = causeOfType(failure, IllegalStateException.class);
        assertThat(notFound).isNotNull();
        assertThat(notFound.getMessage()).contains("worker to update was not found");
    }

    private static <T extends Throwable> T causeOfType(
            Throwable failure,
            Class<T> expectedType
    ) {
        Throwable current = failure;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return expectedType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private record ContextProbe(
            int backendPid,
            String companyId,
            boolean workerAVisible,
            boolean workerBVisible
    ) {
    }
}
