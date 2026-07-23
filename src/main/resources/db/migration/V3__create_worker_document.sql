CREATE TABLE worker (
    worker_id UUID NOT NULL,
    company_id UUID NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    nationality_code VARCHAR(10),
    preferred_language VARCHAR(20),
    work_status VARCHAR(20) NOT NULL,
    visa_expiry_date DATE,
    contract_start_date DATE,
    contract_end_date DATE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_worker PRIMARY KEY (worker_id),
    CONSTRAINT uq_worker_id_company UNIQUE (worker_id, company_id),
    CONSTRAINT fk_worker_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_worker_display_name_not_blank CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT ck_worker_work_status
        CHECK (work_status IN ('ACTIVE', 'ON_LEAVE', 'RESIGNED', 'TERMINATED')),
    CONSTRAINT ck_worker_contract_period CHECK (
        contract_start_date IS NULL OR contract_end_date IS NULL
        OR contract_end_date >= contract_start_date
    ),
    CONSTRAINT ck_worker_version CHECK (version >= 0),
    CONSTRAINT ck_worker_updated_at CHECK (updated_at >= created_at)
);

CREATE TABLE worker_document (
    worker_document_id UUID NOT NULL,
    worker_id UUID NOT NULL,
    company_id UUID NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    submission_status VARCHAR(20) NOT NULL,
    expiry_date DATE,
    destination VARCHAR(120),
    note VARCHAR(500),
    file_id UUID,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_worker_document PRIMARY KEY (worker_document_id),
    CONSTRAINT fk_worker_document_worker
        FOREIGN KEY (worker_id, company_id)
        REFERENCES worker (worker_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_worker_document_type
        CHECK (document_type IN ('PASSPORT_COPY', 'ARC', 'CONTRACT', 'PERMIT')),
    CONSTRAINT ck_worker_document_submission_status
        CHECK (submission_status IN ('MISSING', 'SUBMITTED', 'VERIFIED')),
    CONSTRAINT ck_worker_document_version CHECK (version >= 0),
    CONSTRAINT ck_worker_document_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX idx_worker_company ON worker (company_id);
CREATE INDEX idx_worker_document_worker ON worker_document (worker_id);
CREATE INDEX idx_worker_document_company_status ON worker_document (company_id, submission_status);
