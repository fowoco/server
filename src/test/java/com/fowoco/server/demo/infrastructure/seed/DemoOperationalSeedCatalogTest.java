package com.fowoco.server.demo.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DemoOperationalSeedCatalogTest {

    @Test
    void excludesGoldenFlowOperationalDataWithoutRenumberingShowcaseFixtures() {
        DemoOperationalSeedCatalog catalog = new DemoOperationalSeedCatalog();

        assertThat(catalog.demoTasks().stream().map(TaskSeed::taskId))
                .containsExactlyInAnyOrderElementsOf(idsExcept(
                        "94000000-0000-0000-0000-000000000",
                        1,
                        24,
                        Set.of(6, 7, 8)
                ));
        assertThat(catalog.demoTasks())
                .noneMatch(task -> task.workerId().equals(
                        DemoOperationalSeedCatalog.GOLDEN_FLOW_WORKER_ID
                ));
        assertThat(catalog.demoTasks().stream().map(TaskSeed::caseId))
                .containsExactlyInAnyOrderElementsOf(idsExcept(
                        "94100000-0000-0000-0000-000000000",
                        1,
                        24,
                        Set.of(6, 7, 8)
                ));

        assertThat(catalog.demoDocuments().stream().map(seed -> seed.documentId()))
                .containsExactlyInAnyOrderElementsOf(idsExcept(
                        "95000000-0000-0000-0000-000000000",
                        1,
                        84,
                        Set.of(16, 17, 18)
                ));
        assertThat(catalog.demoDocuments())
                .noneMatch(seed -> seed.workerId().equals(
                        DemoOperationalSeedCatalog.GOLDEN_FLOW_WORKER_ID
                ));
        assertThat(catalog.demoChecklists().stream().map(seed -> seed.checklistItemId()))
                .containsExactlyInAnyOrderElementsOf(idsExcept(
                        "94200000-0000-0000-0000-000000000",
                        1,
                        68,
                        IntStream.rangeClosed(15, 22).boxed().collect(Collectors.toSet())
                ));
        assertThat(catalog.demoApprovals().stream().map(seed -> seed.approvalRequestId()))
                .containsExactlyInAnyOrderElementsOf(idsExcept(
                        "94300000-0000-0000-0000-000000000",
                        1,
                        13,
                        Set.of(2)
                ));
        assertThat(catalog.demoTransitions().stream().map(seed -> seed.transitionId()))
                .containsExactlyInAnyOrderElementsOf(idsExcept(
                        "94400000-0000-0000-0000-000000000",
                        1,
                        52,
                        IntStream.rangeClosed(13, 16).boxed().collect(Collectors.toSet())
                ));
        assertThat(catalog.demoDocumentRequestDrafts().stream().map(seed -> seed.draftId()))
                .containsExactlyInAnyOrderElementsOf(idsExcept(
                        "94700000-0000-0000-0000-000000000",
                        1,
                        5,
                        Set.of(2)
                ));
        assertThat(catalog.demoAudits().stream().map(seed -> seed.auditEventId()))
                .containsExactlyInAnyOrderElementsOf(idsExcept(
                        "96000000-0000-0000-0000-000000000",
                        1,
                        96,
                        Set.of(24, 25, 26, 27, 28, 45, 83, 94)
                ));

        Set<UUID> activeTaskIds = catalog.demoTasks().stream()
                .map(TaskSeed::taskId)
                .collect(Collectors.toSet());
        assertReferencesActiveTasks(catalog.demoChecklists(), seed -> seed.taskId(), activeTaskIds);
        assertReferencesActiveTasks(catalog.demoApprovals(), seed -> seed.taskId(), activeTaskIds);
        assertReferencesActiveTasks(catalog.demoTransitions(), seed -> seed.taskId(), activeTaskIds);
        assertReferencesActiveTasks(catalog.demoExternalSubmissions(), seed -> seed.taskId(), activeTaskIds);
        assertReferencesActiveTasks(catalog.demoEvidence(), seed -> seed.taskId(), activeTaskIds);
        assertReferencesActiveTasks(
                catalog.demoDocumentRequestDrafts(),
                seed -> seed.taskId(),
                activeTaskIds
        );
    }

    private static <T> void assertReferencesActiveTasks(
            Iterable<T> seeds,
            Function<T, UUID> taskId,
            Set<UUID> activeTaskIds
    ) {
        assertThat(seeds).allMatch(seed -> activeTaskIds.contains(taskId.apply(seed)));
    }

    private static Set<UUID> idsExcept(
            String prefix,
            int first,
            int last,
            Set<Integer> excluded
    ) {
        Set<UUID> ids = new HashSet<>();
        IntStream.rangeClosed(first, last)
                .filter(number -> !excluded.contains(number))
                .mapToObj(number -> UUID.fromString(prefix + "%03d".formatted(number)))
                .forEach(ids::add);
        return ids;
    }
}
