CREATE TABLE ai_run (
    ai_run_id UUID NOT NULL,
    company_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    request_id UUID NOT NULL,
    instruction TEXT NOT NULL,
    instruction_hash VARCHAR(64) NOT NULL,
    idempotency_key_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    analysis_outcome VARCHAR(30),
    detected_intent VARCHAR(80),
    last_error_code VARCHAR(80),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_run PRIMARY KEY (ai_run_id),
    CONSTRAINT uq_ai_run_id_company UNIQUE (ai_run_id, company_id),
    CONSTRAINT uq_ai_run_request UNIQUE (request_id),
    CONSTRAINT uq_ai_run_company_idempotency UNIQUE (company_id, idempotency_key_hash),
    CONSTRAINT fk_ai_run_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_run_requester_company
        FOREIGN KEY (requested_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_run_instruction_not_blank CHECK (CHAR_LENGTH(TRIM(instruction)) > 0),
    CONSTRAINT ck_ai_run_instruction_hash_length CHECK (CHAR_LENGTH(instruction_hash) = 64),
    CONSTRAINT ck_ai_run_idempotency_hash_length CHECK (CHAR_LENGTH(idempotency_key_hash) = 64),
    CONSTRAINT ck_ai_run_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_ai_run_outcome CHECK (
        analysis_outcome IS NULL OR analysis_outcome IN (
            'CONTEXT_REQUIRED', 'NEEDS_INFO', 'REVIEW_REQUIRED'
        )
    ),
    CONSTRAINT ck_ai_run_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_ai_run_version CHECK (version >= 0),
    CONSTRAINT ck_ai_run_updated_at CHECK (updated_at >= created_at)
);

CREATE TABLE ai_attempt (
    ai_attempt_id UUID NOT NULL,
    ai_run_id UUID NOT NULL,
    company_id UUID NOT NULL,
    request_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    phase VARCHAR(20) NOT NULL,
    context_round INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    analysis_input_json TEXT NOT NULL,
    agent_version VARCHAR(100),
    model_provider VARCHAR(80),
    model_name VARCHAR(120),
    model_version VARCHAR(120),
    prompt_version VARCHAR(100),
    context_pack_version VARCHAR(100),
    workflow_catalog_version VARCHAR(100),
    contract_version VARCHAR(100),
    knowledge_version VARCHAR(100),
    provider_attempt_count INTEGER,
    error_code VARCHAR(80),
    latency_ms BIGINT,
    started_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_ai_attempt PRIMARY KEY (ai_attempt_id),
    CONSTRAINT uq_ai_attempt_id_company UNIQUE (ai_attempt_id, company_id),
    CONSTRAINT uq_ai_attempt_run_sequence UNIQUE (ai_run_id, sequence_no),
    CONSTRAINT fk_ai_attempt_run_company
        FOREIGN KEY (ai_run_id, company_id)
        REFERENCES ai_run (ai_run_id, company_id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_attempt_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_ai_attempt_phase CHECK (phase IN ('PLAN', 'ANALYZE')),
    CONSTRAINT ck_ai_attempt_context_round CHECK (context_round >= 0),
    CONSTRAINT ck_ai_attempt_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_ai_attempt_input_not_blank CHECK (CHAR_LENGTH(TRIM(analysis_input_json)) > 0),
    CONSTRAINT ck_ai_attempt_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT ck_ai_attempt_provider_count CHECK (
        provider_attempt_count IS NULL OR provider_attempt_count >= 0
    ),
    CONSTRAINT ck_ai_attempt_completion CHECK (
        (status = 'RUNNING' AND completed_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND completed_at IS NOT NULL)
    )
);

CREATE TABLE ai_question (
    ai_question_id UUID NOT NULL,
    ai_run_id UUID NOT NULL,
    ai_attempt_id UUID NOT NULL,
    company_id UUID NOT NULL,
    slot_key VARCHAR(100) NOT NULL,
    label VARCHAR(500) NOT NULL,
    input_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    required BOOLEAN NOT NULL DEFAULT TRUE,
    answer_value VARCHAR(2000),
    answered_by UUID,
    answered_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ai_question PRIMARY KEY (ai_question_id),
    CONSTRAINT uq_ai_question_attempt_slot UNIQUE (ai_attempt_id, slot_key),
    CONSTRAINT fk_ai_question_run_company
        FOREIGN KEY (ai_run_id, company_id)
        REFERENCES ai_run (ai_run_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_question_attempt_company
        FOREIGN KEY (ai_attempt_id, company_id)
        REFERENCES ai_attempt (ai_attempt_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_question_answered_by_company
        FOREIGN KEY (answered_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_question_slot_not_blank CHECK (CHAR_LENGTH(TRIM(slot_key)) > 0),
    CONSTRAINT ck_ai_question_label_not_blank CHECK (CHAR_LENGTH(TRIM(label)) > 0),
    CONSTRAINT ck_ai_question_input_type CHECK (
        input_type IN ('TEXT', 'DATE', 'NUMBER', 'SELECT')
    ),
    CONSTRAINT ck_ai_question_answer CHECK (
        (answer_value IS NULL AND answered_by IS NULL AND answered_at IS NULL)
        OR (answer_value IS NOT NULL AND answered_by IS NOT NULL AND answered_at IS NOT NULL)
    )
);

CREATE TABLE ai_candidate (
    ai_candidate_id UUID NOT NULL,
    ai_run_id UUID NOT NULL,
    ai_attempt_id UUID NOT NULL,
    company_id UUID NOT NULL,
    candidate_ref VARCHAR(120) NOT NULL,
    worker_id UUID NOT NULL,
    workflow_id VARCHAR(100) NOT NULL,
    extracted_slots_json TEXT NOT NULL,
    missing_slots_json TEXT NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ai_candidate PRIMARY KEY (ai_candidate_id),
    CONSTRAINT uq_ai_candidate_attempt_ref UNIQUE (ai_attempt_id, candidate_ref),
    CONSTRAINT fk_ai_candidate_run_company
        FOREIGN KEY (ai_run_id, company_id)
        REFERENCES ai_run (ai_run_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_candidate_attempt_company
        FOREIGN KEY (ai_attempt_id, company_id)
        REFERENCES ai_attempt (ai_attempt_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_candidate_worker_company
        FOREIGN KEY (worker_id, company_id)
        REFERENCES worker (worker_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_candidate_ref_not_blank CHECK (CHAR_LENGTH(TRIM(candidate_ref)) > 0),
    CONSTRAINT ck_ai_candidate_workflow_not_blank CHECK (CHAR_LENGTH(TRIM(workflow_id)) > 0),
    CONSTRAINT ck_ai_candidate_extracted_not_blank
        CHECK (CHAR_LENGTH(TRIM(extracted_slots_json)) > 0),
    CONSTRAINT ck_ai_candidate_missing_not_blank
        CHECK (CHAR_LENGTH(TRIM(missing_slots_json)) > 0),
    CONSTRAINT ck_ai_candidate_confidence CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE INDEX idx_ai_run_company_created ON ai_run (company_id, created_at);
CREATE INDEX idx_ai_run_company_status ON ai_run (company_id, status, updated_at);
CREATE INDEX idx_ai_attempt_run ON ai_attempt (company_id, ai_run_id, sequence_no);
CREATE INDEX idx_ai_question_run ON ai_question (company_id, ai_run_id);
CREATE INDEX idx_ai_candidate_run ON ai_candidate (company_id, ai_run_id);
