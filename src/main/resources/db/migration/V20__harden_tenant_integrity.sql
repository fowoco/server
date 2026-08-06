ALTER TABLE worker_response_upload
    ADD COLUMN company_id UUID;

ALTER TABLE worker_document_upload_idempotency
    ADD COLUMN company_id UUID;

UPDATE worker_response_upload
SET company_id = (
    SELECT response.company_id
    FROM worker_response response
    WHERE response.response_id = worker_response_upload.response_id
);

UPDATE worker_document_upload_idempotency
SET company_id = (
    SELECT link.company_id
    FROM worker_link link
    WHERE link.worker_link_id = worker_document_upload_idempotency.worker_link_id
);

ALTER TABLE task
    ADD CONSTRAINT uq_task_id_worker_company
        UNIQUE (task_id, worker_id, company_id);

ALTER TABLE worker_link
    ADD CONSTRAINT uq_worker_link_id_company
        UNIQUE (worker_link_id, company_id);

ALTER TABLE worker_response
    ADD CONSTRAINT uq_worker_response_id_company
        UNIQUE (response_id, company_id);

ALTER TABLE stored_file
    ADD CONSTRAINT uq_stored_file_id_company
        UNIQUE (stored_file_id, company_id);

ALTER TABLE worker_response_upload
    ADD CONSTRAINT uq_worker_response_upload_file_company
        UNIQUE (stored_file_id, company_id);

ALTER TABLE worker_document
    ADD CONSTRAINT fk_worker_document_task_worker_company
        FOREIGN KEY (task_id, worker_id, company_id)
        REFERENCES task (task_id, worker_id, company_id) ON DELETE RESTRICT;

ALTER TABLE worker_link
    ADD CONSTRAINT fk_worker_link_replaces_company
        FOREIGN KEY (replaces_link_id, company_id)
        REFERENCES worker_link (worker_link_id, company_id) ON DELETE RESTRICT;

ALTER TABLE worker_response
    ADD CONSTRAINT fk_worker_response_link_company
        FOREIGN KEY (worker_link_id, company_id)
        REFERENCES worker_link (worker_link_id, company_id) ON DELETE RESTRICT;

ALTER TABLE worker_response_upload
    ADD CONSTRAINT fk_worker_response_upload_response_company
        FOREIGN KEY (response_id, company_id)
        REFERENCES worker_response (response_id, company_id) ON DELETE CASCADE;

ALTER TABLE worker_response_upload
    ADD CONSTRAINT fk_worker_response_upload_file_company
        FOREIGN KEY (stored_file_id, company_id)
        REFERENCES stored_file (stored_file_id, company_id) ON DELETE RESTRICT;

ALTER TABLE worker_document_upload_idempotency
    ADD CONSTRAINT fk_worker_document_upload_idempotency_link_company
        FOREIGN KEY (worker_link_id, company_id)
        REFERENCES worker_link (worker_link_id, company_id) ON DELETE RESTRICT;

ALTER TABLE worker_document_upload_idempotency
    ADD CONSTRAINT fk_worker_document_upload_idempotency_file_company
        FOREIGN KEY (stored_file_id, company_id)
        REFERENCES stored_file (stored_file_id, company_id) ON DELETE RESTRICT;

ALTER TABLE worker_document
    DROP CONSTRAINT fk_worker_document_task_company;

ALTER TABLE worker_link
    DROP CONSTRAINT fk_worker_link_replaces;

ALTER TABLE worker_response
    DROP CONSTRAINT fk_worker_response_link;

ALTER TABLE worker_response_upload
    DROP CONSTRAINT fk_worker_response_upload_response;

ALTER TABLE worker_response_upload
    DROP CONSTRAINT fk_worker_response_upload_file;

ALTER TABLE worker_document_upload_idempotency
    DROP CONSTRAINT fk_worker_document_upload_idempotency_link;

ALTER TABLE worker_document_upload_idempotency
    DROP CONSTRAINT fk_worker_document_upload_idempotency_file;

ALTER TABLE worker_response_upload
    ALTER COLUMN company_id SET NOT NULL;

ALTER TABLE worker_document_upload_idempotency
    ALTER COLUMN company_id SET NOT NULL;

CREATE INDEX idx_worker_response_upload_company
    ON worker_response_upload (company_id);

CREATE INDEX idx_worker_document_upload_idempotency_company
    ON worker_document_upload_idempotency (company_id);

CREATE INDEX idx_worker_document_upload_idempotency_file_company
    ON worker_document_upload_idempotency (stored_file_id, company_id);
