CREATE TABLE worker_link (
    worker_link_id UUID NOT NULL,
    task_id UUID NOT NULL,
    company_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    conversation_status VARCHAR(20) NOT NULL,
    assignee_id UUID,
    issued_by UUID NOT NULL,
    replaces_link_id UUID,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_worker_link PRIMARY KEY (worker_link_id),
    CONSTRAINT uq_worker_link_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_worker_link_task_idempotency UNIQUE (task_id, idempotency_key),
    CONSTRAINT fk_worker_link_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_worker_link_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_worker_link_issued_by_company
        FOREIGN KEY (issued_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_worker_link_assignee_company
        FOREIGN KEY (assignee_id, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_worker_link_replaces
        FOREIGN KEY (replaces_link_id) REFERENCES worker_link (worker_link_id) ON DELETE SET NULL,
    CONSTRAINT ck_worker_link_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_worker_link_conversation_status
        CHECK (conversation_status IN ('WAITING_WORKER', 'NEEDS_FOLLOWUP', 'REOPENED')),
    CONSTRAINT ck_worker_link_hash_length CHECK (CHAR_LENGTH(token_hash) = 64),
    CONSTRAINT ck_worker_link_hash_lowercase CHECK (token_hash = LOWER(token_hash)),
    CONSTRAINT ck_worker_link_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_worker_link_version CHECK (version >= 0),
    CONSTRAINT ck_worker_link_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX idx_worker_link_company ON worker_link (company_id);
CREATE INDEX idx_worker_link_task ON worker_link (task_id, company_id);

CREATE TABLE worker_response (
    response_id UUID NOT NULL,
    worker_link_id UUID NOT NULL,
    company_id UUID NOT NULL,
    response_type VARCHAR(30) NOT NULL,
    message VARCHAR(1000),
    idempotency_key VARCHAR(100) NOT NULL,
    received_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_worker_response PRIMARY KEY (response_id),
    CONSTRAINT uq_worker_response_idempotency UNIQUE (worker_link_id, idempotency_key),
    CONSTRAINT fk_worker_response_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_worker_response_link
        FOREIGN KEY (worker_link_id) REFERENCES worker_link (worker_link_id) ON DELETE RESTRICT,
    CONSTRAINT ck_worker_response_type
        CHECK (response_type IN ('ACKNOWLEDGED', 'QUESTION', 'NOT_UNDERSTOOD', 'DOCUMENT_SUBMITTED', 'DIFFICULT'))
);

CREATE INDEX idx_worker_response_link ON worker_response (worker_link_id);
CREATE INDEX idx_worker_response_company ON worker_response (company_id);

CREATE TABLE worker_response_upload (
    response_id UUID NOT NULL,
    stored_file_id UUID NOT NULL,
    CONSTRAINT pk_worker_response_upload PRIMARY KEY (response_id, stored_file_id),
    CONSTRAINT fk_worker_response_upload_response
        FOREIGN KEY (response_id) REFERENCES worker_response (response_id) ON DELETE CASCADE,
    CONSTRAINT fk_worker_response_upload_file
        FOREIGN KEY (stored_file_id) REFERENCES stored_file (stored_file_id) ON DELETE RESTRICT
);
CREATE TABLE worker_document_upload_idempotency (
    worker_link_id UUID NOT NULL,
    client_request_id VARCHAR(100) NOT NULL,
    stored_file_id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_worker_document_upload_idempotency PRIMARY KEY (worker_link_id, client_request_id),
    CONSTRAINT fk_worker_document_upload_idempotency_link
        FOREIGN KEY (worker_link_id) REFERENCES worker_link (worker_link_id) ON DELETE RESTRICT,
    CONSTRAINT fk_worker_document_upload_idempotency_file
        FOREIGN KEY (stored_file_id) REFERENCES stored_file (stored_file_id) ON DELETE RESTRICT
);