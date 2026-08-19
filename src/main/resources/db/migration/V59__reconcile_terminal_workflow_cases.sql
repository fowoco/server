UPDATE workflow_case
   SET lifecycle_status = 'COMPLETED',
       updated_at = CURRENT_TIMESTAMP,
       version = version + 1
 WHERE lifecycle_status = 'ACTIVE'
   AND EXISTS (
       SELECT 1
         FROM task completed_task
        WHERE completed_task.case_id = workflow_case.case_id
          AND completed_task.company_id = workflow_case.company_id
          AND completed_task.status = 'COMPLETED'
   )
   AND NOT EXISTS (
       SELECT 1
         FROM task unfinished_task
        WHERE unfinished_task.case_id = workflow_case.case_id
          AND unfinished_task.company_id = workflow_case.company_id
          AND unfinished_task.status NOT IN ('COMPLETED', 'CANCELLED')
   );

UPDATE workflow_case
   SET lifecycle_status = 'CANCELLED',
       updated_at = CURRENT_TIMESTAMP,
       version = version + 1
 WHERE lifecycle_status = 'ACTIVE'
   AND EXISTS (
       SELECT 1
         FROM task cancelled_task
        WHERE cancelled_task.case_id = workflow_case.case_id
          AND cancelled_task.company_id = workflow_case.company_id
          AND cancelled_task.status = 'CANCELLED'
   )
   AND NOT EXISTS (
       SELECT 1
         FROM task remaining_task
        WHERE remaining_task.case_id = workflow_case.case_id
          AND remaining_task.company_id = workflow_case.company_id
          AND remaining_task.status <> 'CANCELLED'
   );
