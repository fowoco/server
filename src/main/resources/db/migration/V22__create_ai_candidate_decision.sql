ALTER TABLE ai_candidate
    ADD CONSTRAINT uq_ai_candidate_id_company
        UNIQUE (ai_candidate_id, company_id);

CREATE TABLE ai_candidate_decision_batch (
    decision_batch_id UUID NOT NULL,
    ai_run_id UUID NOT NULL,
    company_id UUID NOT NULL,
    decided_by UUID NOT NULL,
    idempotency_key_hash VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    case_id UUID,
    resulting_run_version BIGINT,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_ai_candidate_decision_batch PRIMARY KEY (decision_batch_id),
    CONSTRAINT uq_ai_candidate_decision_batch_id_company
        UNIQUE (decision_batch_id, company_id),
    CONSTRAINT uq_ai_candidate_decision_batch_idempotency
        UNIQUE (company_id, ai_run_id, idempotency_key_hash),
    CONSTRAINT fk_ai_candidate_decision_batch_run_company
        FOREIGN KEY (ai_run_id, company_id)
        REFERENCES ai_run (ai_run_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_candidate_decision_batch_actor_company
        FOREIGN KEY (decided_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_candidate_decision_batch_case_company
        FOREIGN KEY (case_id, company_id)
        REFERENCES workflow_case (case_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_candidate_decision_batch_idempotency_hash
        CHECK (CHAR_LENGTH(idempotency_key_hash) = 64),
    CONSTRAINT ck_ai_candidate_decision_batch_payload_hash
        CHECK (CHAR_LENGTH(payload_hash) = 64),
    CONSTRAINT ck_ai_candidate_decision_batch_version
        CHECK (resulting_run_version IS NULL OR resulting_run_version >= 0),
    CONSTRAINT ck_ai_candidate_decision_batch_completion CHECK (
        (completed_at IS NULL AND resulting_run_version IS NULL)
        OR (completed_at IS NOT NULL AND resulting_run_version IS NOT NULL)
    )
);

CREATE TABLE ai_candidate_decision (
    decision_id UUID NOT NULL,
    decision_batch_id UUID NOT NULL,
    ai_run_id UUID NOT NULL,
    ai_candidate_id UUID NOT NULL,
    company_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ai_candidate_decision PRIMARY KEY (decision_id),
    CONSTRAINT uq_ai_candidate_decision_id_company
        UNIQUE (decision_id, company_id),
    CONSTRAINT uq_ai_candidate_decision_batch_candidate
        UNIQUE (decision_batch_id, ai_candidate_id),
    CONSTRAINT uq_ai_candidate_decision_candidate
        UNIQUE (company_id, ai_candidate_id),
    CONSTRAINT fk_ai_candidate_decision_batch_company
        FOREIGN KEY (decision_batch_id, company_id)
        REFERENCES ai_candidate_decision_batch (decision_batch_id, company_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_ai_candidate_decision_run_company
        FOREIGN KEY (ai_run_id, company_id)
        REFERENCES ai_run (ai_run_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_candidate_decision_candidate_company
        FOREIGN KEY (ai_candidate_id, company_id)
        REFERENCES ai_candidate (ai_candidate_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_candidate_decision_action
        CHECK (action IN ('ACCEPT', 'DISCARD'))
);

CREATE TABLE ai_candidate_decision_task (
    decision_id UUID NOT NULL,
    task_id UUID NOT NULL,
    company_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ai_candidate_decision_task
        PRIMARY KEY (decision_id, task_id),
    CONSTRAINT fk_ai_candidate_decision_task_decision_company
        FOREIGN KEY (decision_id, company_id)
        REFERENCES ai_candidate_decision (decision_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_candidate_decision_task_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_candidate_decision_task_sequence
        CHECK (sequence_no > 0)
);

CREATE INDEX idx_ai_candidate_decision_batch_run
    ON ai_candidate_decision_batch (company_id, ai_run_id, created_at);
CREATE INDEX idx_ai_candidate_decision_run
    ON ai_candidate_decision (company_id, ai_run_id, created_at);
CREATE INDEX idx_ai_candidate_decision_task_task
    ON ai_candidate_decision_task (company_id, task_id);
