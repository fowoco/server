CREATE TABLE worker_import_job (
    import_id UUID NOT NULL,
    company_id UUID NOT NULL,
    source_file_id UUID NOT NULL,
    created_by UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    source_headers_json TEXT NOT NULL,
    mapping_json TEXT NOT NULL,
    create_idempotency_key_hash VARCHAR(64) NOT NULL,
    create_request_hash VARCHAR(64) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    invalid_rows INTEGER NOT NULL DEFAULT 0,
    excluded_rows INTEGER NOT NULL DEFAULT 0,
    committed_rows INTEGER NOT NULL DEFAULT 0,
    source_file_expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_worker_import_job PRIMARY KEY (import_id),
    CONSTRAINT uq_worker_import_job_id_company UNIQUE (import_id, company_id),
    CONSTRAINT uq_worker_import_job_create_key UNIQUE (company_id, create_idempotency_key_hash),
    CONSTRAINT fk_worker_import_job_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_worker_import_job_source_file_company
        FOREIGN KEY (source_file_id, company_id)
        REFERENCES stored_file (stored_file_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_worker_import_job_creator_company
        FOREIGN KEY (created_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_worker_import_job_status CHECK (status IN (
        'UPLOADED', 'MAPPED', 'REVIEW_REQUIRED', 'READY', 'COMMITTED'
    )),
    CONSTRAINT ck_worker_import_job_create_key_hash
        CHECK (CHAR_LENGTH(create_idempotency_key_hash) = 64),
    CONSTRAINT ck_worker_import_job_create_request_hash
        CHECK (CHAR_LENGTH(create_request_hash) = 64),
    CONSTRAINT ck_worker_import_job_counts CHECK (
        total_rows >= 0 AND valid_rows >= 0 AND invalid_rows >= 0
        AND excluded_rows >= 0 AND committed_rows >= 0
        AND valid_rows + invalid_rows + excluded_rows + committed_rows <= total_rows
    ),
    CONSTRAINT ck_worker_import_job_version CHECK (version >= 0),
    CONSTRAINT ck_worker_import_job_time_order CHECK (
        updated_at >= created_at AND source_file_expires_at > created_at
    )
);

CREATE TABLE worker_import_row (
    import_row_id UUID NOT NULL,
    import_id UUID NOT NULL,
    company_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    source_values_json TEXT NOT NULL,
    override_values_json TEXT NOT NULL,
    normalized_values_json TEXT NOT NULL,
    validation_errors_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    worker_id UUID,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_worker_import_row PRIMARY KEY (import_row_id),
    CONSTRAINT uq_worker_import_row_id_company UNIQUE (import_row_id, company_id),
    CONSTRAINT uq_worker_import_row_number UNIQUE (import_id, row_number),
    CONSTRAINT fk_worker_import_row_job_company
        FOREIGN KEY (import_id, company_id)
        REFERENCES worker_import_job (import_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_worker_import_row_worker_company
        FOREIGN KEY (worker_id, company_id)
        REFERENCES worker (worker_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_worker_import_row_number CHECK (row_number >= 2),
    CONSTRAINT ck_worker_import_row_status CHECK (status IN (
        'PENDING', 'VALID', 'INVALID', 'EXCLUDED', 'COMMITTED'
    )),
    CONSTRAINT ck_worker_import_row_worker_status CHECK (
        (status = 'COMMITTED' AND worker_id IS NOT NULL)
        OR (status <> 'COMMITTED' AND worker_id IS NULL)
    ),
    CONSTRAINT ck_worker_import_row_version CHECK (version >= 0),
    CONSTRAINT ck_worker_import_row_time_order CHECK (updated_at >= created_at)
);

CREATE TABLE worker_import_commit_idempotency (
    company_id UUID NOT NULL,
    import_id UUID NOT NULL,
    idempotency_key_hash VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_worker_import_commit_idempotency
        PRIMARY KEY (company_id, import_id, idempotency_key_hash),
    CONSTRAINT fk_worker_import_commit_idempotency_job_company
        FOREIGN KEY (import_id, company_id)
        REFERENCES worker_import_job (import_id, company_id) ON DELETE CASCADE,
    CONSTRAINT ck_worker_import_commit_idempotency_key_hash
        CHECK (CHAR_LENGTH(idempotency_key_hash) = 64),
    CONSTRAINT ck_worker_import_commit_request_hash
        CHECK (CHAR_LENGTH(request_hash) = 64)
);

CREATE INDEX idx_worker_import_job_company_updated
    ON worker_import_job (company_id, updated_at DESC, import_id);

CREATE INDEX idx_worker_import_row_job_status
    ON worker_import_row (company_id, import_id, status, row_number);
