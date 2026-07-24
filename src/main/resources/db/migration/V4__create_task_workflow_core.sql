CREATE TABLE task (
    task_id UUID NOT NULL,
    company_id UUID NOT NULL,
    worker_id UUID NOT NULL,
    case_id UUID NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    workflow_id VARCHAR(100) NOT NULL,
    workflow_catalog_version VARCHAR(80) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    business_data_json TEXT NOT NULL,
    critical_fingerprint VARCHAR(64) NOT NULL,
    content_revision BIGINT NOT NULL DEFAULT 0,
    source VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    due_date DATE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_task PRIMARY KEY (task_id),
    CONSTRAINT uq_task_id_company UNIQUE (task_id, company_id),
    CONSTRAINT fk_task_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_worker_company
        FOREIGN KEY (worker_id, company_id)
        REFERENCES worker (worker_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_created_by_company
        FOREIGN KEY (created_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_updated_by_company
        FOREIGN KEY (updated_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_type CHECK (
        task_type IN (
            'RECONTRACT',
            'EMPLOYMENT_PERIOD_EXTENSION',
            'STAY_PERIOD_EXTENSION'
        )
    ),
    CONSTRAINT ck_task_workflow_id_not_blank CHECK (CHAR_LENGTH(TRIM(workflow_id)) > 0),
    CONSTRAINT ck_task_catalog_version_not_blank
        CHECK (CHAR_LENGTH(TRIM(workflow_catalog_version)) > 0),
    CONSTRAINT ck_task_title_not_blank CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_task_business_data_not_blank
        CHECK (CHAR_LENGTH(TRIM(business_data_json)) > 0),
    CONSTRAINT ck_task_fingerprint_length CHECK (CHAR_LENGTH(critical_fingerprint) = 64),
    CONSTRAINT ck_task_fingerprint_lowercase CHECK (
        critical_fingerprint = LOWER(critical_fingerprint)
    ),
    CONSTRAINT ck_task_content_revision CHECK (content_revision >= 0),
    CONSTRAINT ck_task_source CHECK (
        source IN ('MANUAL', 'SYSTEM_DDAY', 'AI_CANDIDATE')
    ),
    CONSTRAINT ck_task_status CHECK (
        status IN (
            'DRAFT',
            'NEEDS_INFO',
            'READY_FOR_REVIEW',
            'APPROVED',
            'WAITING_WORKER',
            'WAITING_EXTERNAL',
            'COMPLETED',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_task_version CHECK (version >= 0),
    CONSTRAINT ck_task_updated_at CHECK (updated_at >= created_at)
);

CREATE TABLE task_checklist_item (
    checklist_item_id UUID NOT NULL,
    task_id UUID NOT NULL,
    company_id UUID NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    label VARCHAR(300) NOT NULL,
    required BOOLEAN NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_by UUID,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_task_checklist_item PRIMARY KEY (checklist_item_id),
    CONSTRAINT uq_task_checklist_code UNIQUE (task_id, item_code),
    CONSTRAINT fk_task_checklist_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_task_checklist_actor_company
        FOREIGN KEY (completed_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_checklist_code_not_blank CHECK (CHAR_LENGTH(TRIM(item_code)) > 0),
    CONSTRAINT ck_task_checklist_label_not_blank CHECK (CHAR_LENGTH(TRIM(label)) > 0),
    CONSTRAINT ck_task_checklist_completion CHECK (
        (completed = FALSE AND completed_by IS NULL AND completed_at IS NULL)
        OR (completed = TRUE AND completed_by IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_task_checklist_version CHECK (version >= 0),
    CONSTRAINT ck_task_checklist_updated_at CHECK (updated_at >= created_at)
);

CREATE TABLE task_transition_history (
    transition_id UUID NOT NULL,
    task_id UUID NOT NULL,
    company_id UUID NOT NULL,
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    reason VARCHAR(500),
    request_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_task_transition_history PRIMARY KEY (transition_id),
    CONSTRAINT fk_task_transition_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_transition_actor_company
        FOREIGN KEY (actor_id, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_transition_from_status CHECK (
        from_status IN (
            'DRAFT',
            'NEEDS_INFO',
            'READY_FOR_REVIEW',
            'APPROVED',
            'WAITING_WORKER',
            'WAITING_EXTERNAL',
            'COMPLETED',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_task_transition_to_status CHECK (
        to_status IN (
            'DRAFT',
            'NEEDS_INFO',
            'READY_FOR_REVIEW',
            'APPROVED',
            'WAITING_WORKER',
            'WAITING_EXTERNAL',
            'COMPLETED',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_task_transition_changed CHECK (from_status <> to_status),
    CONSTRAINT ck_task_transition_request_not_blank CHECK (CHAR_LENGTH(TRIM(request_id)) > 0)
);

CREATE INDEX idx_task_company_status_due
    ON task (company_id, status, due_date);
CREATE INDEX idx_task_company_worker
    ON task (company_id, worker_id, created_at);
CREATE INDEX idx_task_company_case
    ON task (company_id, case_id);
CREATE INDEX idx_task_checklist_task
    ON task_checklist_item (company_id, task_id);
CREATE INDEX idx_task_transition_task_time
    ON task_transition_history (company_id, task_id, created_at);
