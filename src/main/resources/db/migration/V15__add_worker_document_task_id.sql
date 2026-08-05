ALTER TABLE worker_document
    ADD COLUMN task_id UUID;

ALTER TABLE worker_document
    ADD CONSTRAINT fk_worker_document_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE RESTRICT;

CREATE INDEX idx_worker_document_task ON worker_document (task_id, company_id);
