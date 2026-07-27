CREATE TABLE event_publication (
    event_id UUID NOT NULL,
    company_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_version VARCHAR(20) NOT NULL,
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id UUID NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_id UUID,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(64),
    payload_json TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE,
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMP(6) WITH TIME ZONE,
    last_error_code VARCHAR(80),
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_event_publication PRIMARY KEY (event_id),
    CONSTRAINT uq_event_publication_id_company UNIQUE (event_id, company_id),
    CONSTRAINT fk_event_publication_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_event_publication_type_not_blank
        CHECK (CHAR_LENGTH(TRIM(event_type)) > 0),
    CONSTRAINT ck_event_publication_payload_version_not_blank
        CHECK (CHAR_LENGTH(TRIM(payload_version)) > 0),
    CONSTRAINT ck_event_publication_aggregate_type_not_blank
        CHECK (CHAR_LENGTH(TRIM(aggregate_type)) > 0),
    CONSTRAINT ck_event_publication_actor_type CHECK (
        actor_type IN ('HR_USER', 'WORKER_LINK', 'AI_AGENT', 'SYSTEM_RULE')
    ),
    CONSTRAINT ck_event_publication_request_not_blank
        CHECK (CHAR_LENGTH(TRIM(request_id)) > 0),
    CONSTRAINT ck_event_publication_payload_not_blank
        CHECK (CHAR_LENGTH(TRIM(payload_json)) > 0),
    CONSTRAINT ck_event_publication_status CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'RETRY_WAIT',
            'COMPLETED',
            'REVIEW_REQUIRED'
        )
    ),
    CONSTRAINT ck_event_publication_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_event_publication_version CHECK (version >= 0),
    CONSTRAINT ck_event_publication_next_attempt CHECK (
        (status IN ('PENDING', 'RETRY_WAIT') AND next_attempt_at IS NOT NULL)
        OR (status NOT IN ('PENDING', 'RETRY_WAIT') AND next_attempt_at IS NULL)
    ),
    CONSTRAINT ck_event_publication_lease CHECK (
        (status = 'PROCESSING'
            AND lease_owner IS NOT NULL
            AND CHAR_LENGTH(TRIM(lease_owner)) > 0
            AND lease_expires_at IS NOT NULL)
        OR (status <> 'PROCESSING'
            AND lease_owner IS NULL
            AND lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_event_publication_error CHECK (
        (status IN ('RETRY_WAIT', 'REVIEW_REQUIRED')
            AND last_error_code IS NOT NULL
            AND CHAR_LENGTH(TRIM(last_error_code)) > 0)
        OR (status NOT IN ('RETRY_WAIT', 'REVIEW_REQUIRED')
            AND last_error_code IS NULL)
    ),
    CONSTRAINT ck_event_publication_completed CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_event_publication_timestamps CHECK (
        created_at >= occurred_at AND updated_at >= created_at
    )
);

CREATE TABLE event_consumption (
    consumption_id UUID NOT NULL,
    event_id UUID NOT NULL,
    company_id UUID NOT NULL,
    handler_name VARCHAR(120) NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_event_consumption PRIMARY KEY (consumption_id),
    CONSTRAINT uq_event_consumption_event_handler UNIQUE (event_id, handler_name),
    CONSTRAINT fk_event_consumption_publication
        FOREIGN KEY (event_id, company_id)
        REFERENCES event_publication (event_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_event_consumption_handler_not_blank
        CHECK (CHAR_LENGTH(TRIM(handler_name)) > 0)
);

CREATE INDEX idx_event_publication_claim
    ON event_publication (status, next_attempt_at, lease_expires_at, occurred_at);
CREATE INDEX idx_event_publication_company_time
    ON event_publication (company_id, occurred_at);
CREATE INDEX idx_event_publication_request
    ON event_publication (company_id, request_id);
CREATE INDEX idx_event_publication_review
    ON event_publication (status, updated_at);
CREATE INDEX idx_event_consumption_company_event
    ON event_consumption (company_id, event_id);
