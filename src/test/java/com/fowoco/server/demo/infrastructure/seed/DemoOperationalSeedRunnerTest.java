package com.fowoco.server.demo.infrastructure.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.common.security.TenantTransactionExecutor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;

class DemoOperationalSeedRunnerTest {

    private static final UUID DEMO_COMPANY_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID TEST_COMPANY_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000001");

    @Test
    void seedsDemoAndTestDatasetsInSeparateOrderedTenantTransactions() throws Exception {
        TenantTransactionExecutor transactionExecutor = mock(TenantTransactionExecutor.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(transactionExecutor).execute(any(UUID.class), any(Runnable.class));

        DemoOperationalSeedRunner runner = new DemoOperationalSeedRunner(
                properties(),
                Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC),
                new DemoOperationalSeedCatalog(),
                mock(DemoGoldenFlowSeedStateGuard.class),
                mock(DemoCaseSeeder.class),
                mock(DemoTaskSeeder.class),
                mock(DemoWorkerDocumentSeeder.class),
                mock(DemoTaskChecklistSeeder.class),
                mock(DemoApprovalRequestSeeder.class),
                mock(DemoStoredFileSeeder.class),
                mock(DemoTaskTransitionSeeder.class),
                mock(DemoExternalSubmissionSeeder.class),
                mock(DemoEvidenceSeeder.class),
                mock(DemoDocumentRequestDraftSeeder.class),
                mock(DemoAuditEventSeeder.class),
                mock(DemoOperationalSeedVerifier.class),
                transactionExecutor
        );

        runner.run(new DefaultApplicationArguments(new String[0]));

        InOrder order = inOrder(transactionExecutor);
        order.verify(transactionExecutor).execute(eq(DEMO_COMPANY_ID), any(Runnable.class));
        order.verify(transactionExecutor).execute(eq(TEST_COMPANY_ID), any(Runnable.class));
    }

    private DemoAuthSeedProperties properties() {
        return new DemoAuthSeedProperties(
                true,
                DEMO_COMPANY_ID,
                "FOWOCO Demo Company",
                TEST_COMPANY_ID,
                "FOWOCO Test Company",
                UUID.fromString("90000000-0000-0000-0000-000000000002"),
                "데모 관리자",
                "demo.admin@example.com",
                "Demo-password-1!"
        );
    }
}
