package com.fowoco.server.airun.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.airun.application.AiRunResult;
import com.fowoco.server.airun.application.error.AiRunErrorCode;
import com.fowoco.server.airun.domain.AiRunStatus;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiRunEventStreamBrokerTest {

    @Test
    void limitsConnectionsForTheSameUserAndRun() {
        AiRunEventStreamBroker broker = new AiRunEventStreamBroker(
                new AiRunEventStreamProperties(
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(10),
                        20,
                        1
                ),
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC)
        );
        UUID actorId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID companyId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID aiRunId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        ActorContext actor = new ActorContext(actorId, companyId, Set.of(UserRole.HR));
        AiRunResult running = new AiRunResult(
                aiRunId,
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                "원문은 event에 포함하지 않음",
                AiRunStatus.RUNNING,
                null,
                null,
                null,
                1,
                0,
                List.of(),
                List.of(),
                Instant.parse("2026-08-06T00:00:00Z"),
                Instant.parse("2026-08-06T00:00:00Z")
        );

        broker.subscribe(actor, running, null);

        assertThatThrownBy(() -> broker.subscribe(actor, running, null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(AiRunErrorCode.AI_RUN_SSE_CONNECTION_LIMIT)
                );
    }
}
