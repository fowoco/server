CREATE TABLE approval_request (
    approval_request_id UUID NOT NULL,
    task_id UUID NOT NULL,
    company_id UUID NOT NULL,
    target_task_version BIGINT NOT NULL,
    approved_task_version BIGINT,
    target_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ai_snapshot_json TEXT,
    hr_snapshot_json TEXT NOT NULL,
    changed_fields_json TEXT NOT NULL,
    source_versions_json TEXT NOT NULL,
    requested_by UUID NOT NULL,
    requested_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    decided_by UUID,
    decided_at TIMESTAMP(6) WITH TIME ZONE,
    decision_reason VARCHAR(500),
    invalidated_at TIMESTAMP(6) WITH TIME ZONE,
    invalidation_reason VARCHAR(500),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_request PRIMARY KEY (approval_request_id),
    CONSTRAINT uq_approval_request_id_task_company
        UNIQUE (approval_request_id, task_id, company_id),
    CONSTRAINT fk_approval_request_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_request_requester_company
        FOREIGN KEY (requested_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_request_decider_company
        FOREIGN KEY (decided_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_approval_request_target_version CHECK (target_task_version >= 0),
    CONSTRAINT ck_approval_request_approved_version CHECK (
        approved_task_version IS NULL OR approved_task_version >= target_task_version
    ),
    CONSTRAINT ck_approval_request_fingerprint_length
        CHECK (CHAR_LENGTH(target_fingerprint) = 64),
    CONSTRAINT ck_approval_request_fingerprint_lowercase
        CHECK (target_fingerprint = LOWER(target_fingerprint)),
    CONSTRAINT ck_approval_request_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'INVALIDATED')
    ),
    CONSTRAINT ck_approval_request_hr_snapshot_not_blank
        CHECK (CHAR_LENGTH(TRIM(hr_snapshot_json)) > 0),
    CONSTRAINT ck_approval_request_changed_fields_not_blank
        CHECK (CHAR_LENGTH(TRIM(changed_fields_json)) > 0),
    CONSTRAINT ck_approval_request_source_versions_not_blank
        CHECK (CHAR_LENGTH(TRIM(source_versions_json)) > 0),
    CONSTRAINT ck_approval_request_decision CHECK (
        (status = 'PENDING'
            AND decided_by IS NULL
            AND decided_at IS NULL
            AND approved_task_version IS NULL
            AND invalidated_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED')
            AND decided_by IS NOT NULL
            AND decided_at IS NOT NULL
            AND invalidated_at IS NULL)
        OR (status = 'INVALIDATED' AND invalidated_at IS NOT NULL)
    ),
    CONSTRAINT ck_approval_request_approved_task_version CHECK (
        status <> 'APPROVED' OR approved_task_version IS NOT NULL
    ),
    CONSTRAINT ck_approval_request_version CHECK (version >= 0),
    CONSTRAINT ck_approval_request_updated_at CHECK (updated_at >= created_at)
);

CREATE TABLE external_submission (
    external_submission_id UUID NOT NULL,
    task_id UUID NOT NULL,
    company_id UUID NOT NULL,
    destination VARCHAR(160) NOT NULL,
    safe_reference VARCHAR(300) NOT NULL,
    submitted_by UUID NOT NULL,
    submitted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_external_submission PRIMARY KEY (external_submission_id),
    CONSTRAINT uq_external_submission_id_task_company
        UNIQUE (external_submission_id, task_id, company_id),
    CONSTRAINT fk_external_submission_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_external_submission_actor_company
        FOREIGN KEY (submitted_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_external_submission_destination_not_blank
        CHECK (CHAR_LENGTH(TRIM(destination)) > 0),
    CONSTRAINT ck_external_submission_reference_not_blank
        CHECK (CHAR_LENGTH(TRIM(safe_reference)) > 0),
    CONSTRAINT ck_external_submission_created_at
        CHECK (created_at >= submitted_at)
);

CREATE TABLE task_evidence (
    evidence_id UUID NOT NULL,
    task_id UUID NOT NULL,
    company_id UUID NOT NULL,
    evidence_type VARCHAR(30) NOT NULL,
    file_reference VARCHAR(300),
    note VARCHAR(500),
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_task_evidence PRIMARY KEY (evidence_id),
    CONSTRAINT uq_task_evidence_id_task_company
        UNIQUE (evidence_id, task_id, company_id),
    CONSTRAINT fk_task_evidence_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_evidence_actor_company
        FOREIGN KEY (recorded_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_evidence_type CHECK (
        evidence_type IN ('DOCUMENT', 'RECEIPT', 'OFFICIAL_RESULT', 'HR_CONFIRMATION')
    ),
    CONSTRAINT ck_task_evidence_content CHECK (
        (file_reference IS NOT NULL AND CHAR_LENGTH(TRIM(file_reference)) > 0)
        OR (note IS NOT NULL AND CHAR_LENGTH(TRIM(note)) > 0)
    ),
    CONSTRAINT ck_task_evidence_created_at CHECK (created_at >= recorded_at)
);

CREATE TABLE audit_event (
    audit_event_id UUID NOT NULL,
    company_id UUID NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_id UUID,
    user_role VARCHAR(20),
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(64),
    event_version VARCHAR(30) NOT NULL,
    change_summary VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (audit_event_id),
    CONSTRAINT fk_audit_event_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_audit_event_actor_type CHECK (
        actor_type IN ('HR_USER', 'WORKER_LINK', 'AI_AGENT', 'SYSTEM_RULE')
    ),
    CONSTRAINT ck_audit_event_user_role CHECK (
        user_role IS NULL OR user_role IN ('ADMIN', 'HR', 'VIEWER')
    ),
    CONSTRAINT ck_audit_event_action_not_blank CHECK (CHAR_LENGTH(TRIM(action)) > 0),
    CONSTRAINT ck_audit_event_target_type_not_blank CHECK (CHAR_LENGTH(TRIM(target_type)) > 0),
    CONSTRAINT ck_audit_event_request_not_blank CHECK (CHAR_LENGTH(TRIM(request_id)) > 0),
    CONSTRAINT ck_audit_event_version_not_blank CHECK (CHAR_LENGTH(TRIM(event_version)) > 0),
    CONSTRAINT ck_audit_event_summary_not_blank CHECK (CHAR_LENGTH(TRIM(change_summary)) > 0)
);

CREATE INDEX idx_approval_request_task_status
    ON approval_request (company_id, task_id, status, requested_at);
CREATE INDEX idx_external_submission_task
    ON external_submission (company_id, task_id, submitted_at);
CREATE INDEX idx_task_evidence_task
    ON task_evidence (company_id, task_id, recorded_at);
CREATE INDEX idx_audit_event_company_time
    ON audit_event (company_id, created_at);
CREATE INDEX idx_audit_event_target_time
    ON audit_event (company_id, target_type, target_id, created_at);
CREATE INDEX idx_audit_event_actor_time
    ON audit_event (company_id, actor_type, actor_id, created_at);
CREATE INDEX idx_audit_event_trace
    ON audit_event (company_id, trace_id);
