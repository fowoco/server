ALTER TABLE stored_file
    ADD COLUMN checksum_sha256 VARCHAR(64);

ALTER TABLE stored_file
    ADD COLUMN updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE stored_file
    ADD CONSTRAINT ck_stored_file_checksum_sha256
        CHECK (
            checksum_sha256 IS NULL
            OR (
                CHAR_LENGTH(checksum_sha256) = 64
                AND checksum_sha256 = LOWER(checksum_sha256)
            )
        );

ALTER TABLE worker_document
    ADD COLUMN issue_date DATE;

ALTER TABLE worker_document
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'LEGACY';

ALTER TABLE worker_document
    ADD CONSTRAINT ck_worker_document_issue_expiry
        CHECK (issue_date IS NULL OR expiry_date IS NULL OR expiry_date >= issue_date);

ALTER TABLE worker_document
    ADD CONSTRAINT ck_worker_document_source
        CHECK (source IN ('LEGACY', 'DEMO_SEED', 'HR_UPLOAD', 'WORKER_UPLOAD', 'AI_GENERATED'));

ALTER TABLE worker_document
    DROP CONSTRAINT ck_worker_document_type;

ALTER TABLE worker_document
    ADD CONSTRAINT ck_worker_document_type
        CHECK (document_type IN (
            'PASSPORT_COPY',
            'ARC',
            'CONTRACT',
            'PERMIT',
            'EMPLOYMENT_EXTENSION_APPLICATION',
            'INTEGRATED_APPLICATION',
            'RESIDENCE_PROOF'
        ));

ALTER TABLE worker_document
    DROP CONSTRAINT ck_worker_document_submission_status;

ALTER TABLE worker_document
    ADD CONSTRAINT ck_worker_document_submission_status
        CHECK (submission_status IN ('DRAFT', 'MISSING', 'SUBMITTED', 'VERIFIED'));

CREATE INDEX idx_worker_document_company_expiry
    ON worker_document (company_id, expiry_date);

CREATE INDEX idx_worker_document_company_source
    ON worker_document (company_id, source);
