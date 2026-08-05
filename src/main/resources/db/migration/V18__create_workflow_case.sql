CREATE TABLE workflow_case (
    case_id UUID NOT NULL,
    company_id UUID NOT NULL,
    worker_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    workflow_catalog_version VARCHAR(80) NOT NULL,
    workflow_snapshot_json TEXT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_workflow_case PRIMARY KEY (case_id),
    CONSTRAINT uq_workflow_case_id_company UNIQUE (case_id, company_id),
    CONSTRAINT fk_workflow_case_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_workflow_case_worker_company
        FOREIGN KEY (worker_id, company_id)
        REFERENCES worker (worker_id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_case_created_by_company
        FOREIGN KEY (created_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_workflow_case_title_not_blank CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_workflow_case_lifecycle_status CHECK (
        lifecycle_status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT ck_workflow_case_priority CHECK (
        priority IN ('URGENT', 'HIGH', 'NORMAL', 'LOW')
    ),
    CONSTRAINT ck_workflow_case_catalog_version_not_blank
        CHECK (CHAR_LENGTH(TRIM(workflow_catalog_version)) > 0),
    CONSTRAINT ck_workflow_case_snapshot_not_blank
        CHECK (CHAR_LENGTH(TRIM(workflow_snapshot_json)) > 0),
    CONSTRAINT ck_workflow_case_version CHECK (version >= 0),
    CONSTRAINT ck_workflow_case_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX idx_workflow_case_company_updated
    ON workflow_case (company_id, updated_at);
CREATE INDEX idx_workflow_case_company_worker
    ON workflow_case (company_id, worker_id);
