ALTER TABLE worker_document
    ADD CONSTRAINT uq_worker_document_id_company
        UNIQUE (worker_document_id, company_id);

CREATE TABLE document_ocr_run (
    ocr_run_id UUID NOT NULL,
    company_id UUID NOT NULL,
    worker_document_id UUID NOT NULL,
    stored_file_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    runtime_request_id UUID NOT NULL,
    idempotency_key_hash VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    country_code VARCHAR(3),
    status VARCHAR(30) NOT NULL,
    result_ciphertext TEXT,
    result_key_version VARCHAR(60),
    corrected_fields_ciphertext TEXT,
    corrected_fields_key_version VARCHAR(60),
    last_error_code VARCHAR(80),
    reviewed_by UUID,
    review_reason VARCHAR(300),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    reviewed_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_document_ocr_run PRIMARY KEY (ocr_run_id),
    CONSTRAINT uq_document_ocr_run_id_company UNIQUE (ocr_run_id, company_id),
    CONSTRAINT uq_document_ocr_run_runtime_request UNIQUE (runtime_request_id),
    CONSTRAINT uq_document_ocr_run_idempotency
        UNIQUE (company_id, idempotency_key_hash),
    CONSTRAINT fk_document_ocr_run_document_company
        FOREIGN KEY (worker_document_id, company_id)
        REFERENCES worker_document (worker_document_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_ocr_run_file_company
        FOREIGN KEY (stored_file_id, company_id)
        REFERENCES stored_file (stored_file_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_ocr_run_requester_company
        FOREIGN KEY (requested_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_ocr_run_reviewer_company
        FOREIGN KEY (reviewed_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_document_ocr_run_idempotency_hash
        CHECK (CHAR_LENGTH(idempotency_key_hash) = 64),
    CONSTRAINT ck_document_ocr_run_request_hash
        CHECK (CHAR_LENGTH(request_hash) = 64),
    CONSTRAINT ck_document_ocr_run_document_type
        CHECK (document_type IN ('PASSPORT_COPY', 'ARC')),
    CONSTRAINT ck_document_ocr_run_country_code
        CHECK (
            (document_type = 'PASSPORT_COPY' AND country_code IS NOT NULL AND CHAR_LENGTH(country_code) = 3)
            OR (document_type = 'ARC' AND country_code IS NULL)
        ),
    CONSTRAINT ck_document_ocr_run_status
        CHECK (status IN (
            'QUEUED', 'RUNNING', 'READY_FOR_REVIEW', 'REVIEW_REQUIRED',
            'APPROVED', 'REJECTED', 'FAILED'
        )),
    CONSTRAINT ck_document_ocr_run_result_pair
        CHECK (
            (result_ciphertext IS NULL AND result_key_version IS NULL)
            OR (result_ciphertext IS NOT NULL AND result_key_version IS NOT NULL)
        ),
    CONSTRAINT ck_document_ocr_run_correction_pair
        CHECK (
            (corrected_fields_ciphertext IS NULL AND corrected_fields_key_version IS NULL)
            OR (corrected_fields_ciphertext IS NOT NULL AND corrected_fields_key_version IS NOT NULL)
        ),
    CONSTRAINT ck_document_ocr_run_review_pair
        CHECK (
            (reviewed_by IS NULL AND reviewed_at IS NULL)
            OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
        ),
    CONSTRAINT ck_document_ocr_run_review_reason
        CHECK (review_reason IS NULL OR CHAR_LENGTH(TRIM(review_reason)) BETWEEN 1 AND 300),
    CONSTRAINT ck_document_ocr_run_version CHECK (version >= 0),
    CONSTRAINT ck_document_ocr_run_time_order CHECK (
        updated_at >= created_at
        AND (started_at IS NULL OR started_at >= created_at)
        AND (completed_at IS NULL OR completed_at >= created_at)
        AND (reviewed_at IS NULL OR reviewed_at >= created_at)
    )
);

CREATE INDEX idx_document_ocr_run_document_created
    ON document_ocr_run (company_id, worker_document_id, created_at DESC, ocr_run_id);

CREATE INDEX idx_document_ocr_run_company_status
    ON document_ocr_run (company_id, status, updated_at DESC);
