package com.fowoco.server.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.task.application.error.TaskErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskChecklistItemTest {

    private static final UUID ITEM_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000002");
    private static final UUID COMPANY_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000003");
    private static final UUID ACTOR_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-07-23T01:00:00Z");

    @Test
    void recordsAndClearsCompletionMetadataTogether() {
        TaskChecklistItem item = item(0);

        assertThat(item.updateCompletion(true, 0, ACTOR_ID, NOW.plusSeconds(1))).isTrue();
        assertThat(item.completed()).isTrue();
        assertThat(item.completedBy()).isEqualTo(ACTOR_ID);
        assertThat(item.completedAt()).isEqualTo(NOW.plusSeconds(1));

        assertThat(item.updateCompletion(false, 0, ACTOR_ID, NOW.plusSeconds(2))).isTrue();
        assertThat(item.completed()).isFalse();
        assertThat(item.completedBy()).isNull();
        assertThat(item.completedAt()).isNull();
    }

    @Test
    void rejectsStaleChecklistVersion() {
        TaskChecklistItem item = item(2);

        assertThatThrownBy(() ->
                item.updateCompletion(true, 1, ACTOR_ID, NOW.plusSeconds(1)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(TaskErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    void rejectsPartiallyPersistedCompletionMetadata() {
        assertThatThrownBy(() -> new TaskChecklistItem(
                ITEM_ID,
                TASK_ID,
                COMPANY_ID,
                "REQUIRED_ITEM",
                "필수 확인",
                true,
                false,
                ACTOR_ID,
                null,
                NOW,
                NOW,
                0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private TaskChecklistItem item(long version) {
        return new TaskChecklistItem(
                ITEM_ID,
                TASK_ID,
                COMPANY_ID,
                "REQUIRED_ITEM",
                "필수 확인",
                true,
                false,
                null,
                null,
                NOW,
                NOW,
                version
        );
    }
}
