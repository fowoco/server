package com.fowoco.server.task.application.port;

import com.fowoco.server.task.domain.Task;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import java.time.LocalDate;

public interface TaskCaseRegistrar {

    void register(Task task, WorkflowDefinition workflow, LocalDate today);
}
