package com.fowoco.server.demo.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class DemoCaseSeederSnapshotTest {

    private static final UUID COMPOUND_CASE_ID =
            UUID.fromString("94100000-0000-0000-0000-000000000006");

    @Test
    void keepsCandidateOrderAndAllowlistedWorkflowConditions() {
        List<TaskSeed> compoundTasks = new DemoOperationalSeedCatalog().demoTasks().stream()
                .filter(task -> task.caseId().equals(COMPOUND_CASE_ID))
                .toList();
        DemoCaseSeeder seeder = new DemoCaseSeeder(
                mock(JdbcTemplate.class),
                new ObjectMapper()
        );

        String snapshot = seeder.snapshot(compoundTasks);

        assertThat(JsonPath.<List<Integer>>read(snapshot, "$.steps[*].order"))
                .containsExactly(1, 2, 3);
        assertThat(JsonPath.<List<String>>read(snapshot, "$.steps[*].task_id"))
                .containsExactly(
                        "94000000-0000-0000-0000-000000000006",
                        "94000000-0000-0000-0000-000000000008",
                        "94000000-0000-0000-0000-000000000007"
                );
        assertThat(JsonPath.<Boolean>read(
                snapshot,
                "$.steps[0].required_conditions.approval_required"
        )).isTrue();
        assertThat(JsonPath.<String>read(
                snapshot,
                "$.steps[2].required_conditions.depends_on_task_id"
        )).isEqualTo("94000000-0000-0000-0000-000000000006");
    }
}
