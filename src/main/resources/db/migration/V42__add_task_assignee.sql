ALTER TABLE task
    ADD COLUMN assignee_id UUID;

UPDATE task
SET assignee_id = created_by
WHERE assignee_id IS NULL;

ALTER TABLE task
    ADD CONSTRAINT fk_task_assignee_company
        FOREIGN KEY (assignee_id, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT;

CREATE INDEX idx_task_company_assignee
    ON task (company_id, assignee_id);
