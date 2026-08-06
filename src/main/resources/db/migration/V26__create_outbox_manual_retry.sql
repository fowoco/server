CREATE TABLE outbox_manual_retry (
    manual_retry_id UUID NOT NULL,
    company_id UUID NOT NULL,
    event_id UUID NOT NULL,
    idempotency_key_hash VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    reason VARCHAR(300) NOT NULL,
    requested_by UUID NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(32),
    previous_attempt_count INTEGER NOT NULL DEFAULT 0,
    accepted_status VARCHAR(30) NOT NULL,
    accepted_version BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_outbox_manual_retry PRIMARY KEY (manual_retry_id),
    CONSTRAINT uq_outbox_manual_retry_event_key
        UNIQUE (company_id, event_id, idempotency_key_hash),
    CONSTRAINT fk_outbox_manual_retry_event_company
        FOREIGN KEY (event_id, company_id)
        REFERENCES event_publication (event_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_outbox_manual_retry_actor_company
        FOREIGN KEY (requested_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_outbox_manual_retry_key_hash
        CHECK (CHAR_LENGTH(idempotency_key_hash) = 64),
    CONSTRAINT ck_outbox_manual_retry_request_hash
        CHECK (CHAR_LENGTH(request_hash) = 64),
    CONSTRAINT ck_outbox_manual_retry_reason
        CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 10 AND 300),
    CONSTRAINT ck_outbox_manual_retry_request_id
        CHECK (CHAR_LENGTH(TRIM(request_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_outbox_manual_retry_trace_id
        CHECK (trace_id IS NULL OR CHAR_LENGTH(trace_id) = 32),
    CONSTRAINT ck_outbox_manual_retry_previous_attempt_count
        CHECK (previous_attempt_count >= 0),
    CONSTRAINT ck_outbox_manual_retry_status
        CHECK (accepted_status = 'PENDING'),
    CONSTRAINT ck_outbox_manual_retry_version
        CHECK (accepted_version >= 0)
);

CREATE INDEX idx_outbox_manual_retry_company_created
    ON outbox_manual_retry (company_id, created_at);

CREATE INDEX idx_outbox_manual_retry_event_created
    ON outbox_manual_retry (company_id, event_id, created_at);
