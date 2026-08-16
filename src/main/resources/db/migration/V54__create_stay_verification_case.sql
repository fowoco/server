CREATE TABLE stay_verification_case (
    stay_verification_id UUID NOT NULL,
    company_id UUID NOT NULL,
    worker_id UUID NOT NULL,
    source_stay_expiry_date DATE NOT NULL,
    verification_status VARCHAR(30) NOT NULL,
    status_checked_at TIMESTAMP(6) WITH TIME ZONE,
    extension_applied_at DATE,
    extension_receipt_document_id UUID,
    approval_result_document_id UUID,
    new_stay_expiry_date DATE,
    official_consultation_note VARCHAR(1000),
    employment_end_confirmed_at TIMESTAMP(6) WITH TIME ZONE,
    recheck_date DATE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_stay_verification_case PRIMARY KEY (stay_verification_id),
    CONSTRAINT uq_stay_verification_worker_expiry
        UNIQUE (company_id, worker_id, source_stay_expiry_date),
    CONSTRAINT fk_stay_verification_worker_company
        FOREIGN KEY (worker_id, company_id)
        REFERENCES worker (worker_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stay_verification_extension_receipt
        FOREIGN KEY (extension_receipt_document_id, company_id)
        REFERENCES worker_document (worker_document_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stay_verification_approval_result
        FOREIGN KEY (approval_result_document_id, company_id)
        REFERENCES worker_document (worker_document_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_stay_verification_status CHECK (
        verification_status IN (
            'APPROVED', 'APPLICATION_PENDING', 'UNKNOWN', 'NOT_APPLIED', 'EMPLOYMENT_ENDED'
        )
    ),
    CONSTRAINT ck_stay_verification_note CHECK (
        official_consultation_note IS NULL
        OR CHAR_LENGTH(TRIM(official_consultation_note)) > 0
    ),
    CONSTRAINT ck_stay_verification_new_expiry CHECK (
        new_stay_expiry_date IS NULL OR new_stay_expiry_date > source_stay_expiry_date
    ),
    CONSTRAINT ck_stay_verification_version CHECK (version >= 0),
    CONSTRAINT ck_stay_verification_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX idx_stay_verification_company_status
    ON stay_verification_case (company_id, verification_status, updated_at DESC);

CREATE INDEX idx_stay_verification_company_worker
    ON stay_verification_case (company_id, worker_id, created_at DESC);
