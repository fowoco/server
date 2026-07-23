CREATE TABLE worker (
    worker_id UUID NOT NULL,
    company_id UUID NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    nationality VARCHAR(80) NOT NULL,
    preferred_language VARCHAR(20) NOT NULL,
    employment_status VARCHAR(20) NOT NULL,
    stay_expiry_date DATE NOT NULL,
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
    CONSTRAINT ck_worker_nationality_not_blank CHECK (CHAR_LENGTH(TRIM(nationality)) > 0),
    CONSTRAINT ck_worker_language_not_blank CHECK (CHAR_LENGTH(TRIM(preferred_language)) > 0),
    CONSTRAINT ck_worker_employment_status
        CHECK (employment_status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED')),
    CONSTRAINT ck_worker_contract_period CHECK (
        contract_start_date IS NULL
        OR contract_end_date IS NULL
        OR contract_end_date >= contract_start_date
    ),
    CONSTRAINT ck_worker_version CHECK (version >= 0),
    CONSTRAINT ck_worker_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX idx_worker_company_status
    ON worker (company_id, employment_status);
CREATE INDEX idx_worker_company_stay_expiry
    ON worker (company_id, stay_expiry_date);
