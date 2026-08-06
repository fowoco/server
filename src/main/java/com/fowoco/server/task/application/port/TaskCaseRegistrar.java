package com.fowoco.server.task.application.port;

import com.fowoco.server.task.domain.Task;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import java.time.LocalDate;
import java.util.List;

public interface TaskCaseRegistrar {

    void register(Task task, WorkflowDefinition workflow, LocalDate today);

    void registerComposite(List<CaseTask> caseTasks, LocalDate today);

    record CaseTask(Task task, WorkflowDefinition workflow) {
    }
}
