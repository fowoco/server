ALTER TABLE worker_document
    ADD COLUMN archived_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE worker_document
    ADD COLUMN archived_by UUID;

ALTER TABLE worker_document
    ADD COLUMN archive_reason VARCHAR(500);

ALTER TABLE worker_document
    ADD CONSTRAINT fk_worker_document_archived_by_company
        FOREIGN KEY (archived_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT;

ALTER TABLE worker_document
    ADD CONSTRAINT ck_worker_document_archive_metadata CHECK (
        (archived_at IS NULL AND archived_by IS NULL AND archive_reason IS NULL)
        OR (
            archived_at IS NOT NULL
            AND archived_by IS NOT NULL
            AND archive_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(archive_reason)) > 0
        )
    );

CREATE INDEX idx_worker_document_company_archive
    ON worker_document (company_id, archived_at, updated_at DESC);
