CREATE TABLE document_request_draft (
    draft_id UUID NOT NULL,
    task_id UUID NOT NULL,
    company_id UUID NOT NULL,
    language VARCHAR(20) NOT NULL,
    message VARCHAR(1000),
    review_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_document_request_draft PRIMARY KEY (draft_id),
    CONSTRAINT uq_document_request_draft_task UNIQUE (task_id, company_id),
    CONSTRAINT fk_document_request_draft_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_request_draft_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_document_request_draft_language_not_blank
        CHECK (CHAR_LENGTH(TRIM(language)) > 0),
    CONSTRAINT ck_document_request_draft_review_status
        CHECK (review_status IN ('DRAFT')),
    CONSTRAINT ck_document_request_draft_version CHECK (version >= 0),
    CONSTRAINT ck_document_request_draft_updated_at CHECK (updated_at >= created_at)
);

CREATE TABLE document_request_draft_type (
    draft_id UUID NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    CONSTRAINT fk_document_request_draft_type_draft
        FOREIGN KEY (draft_id) REFERENCES document_request_draft (draft_id) ON DELETE CASCADE,
    CONSTRAINT ck_document_request_draft_type_value
        CHECK (document_type IN ('PASSPORT_COPY', 'ARC', 'CONTRACT', 'PERMIT')),
    CONSTRAINT uq_document_request_draft_type UNIQUE (draft_id, document_type)
);

CREATE INDEX idx_document_request_draft_company ON document_request_draft (company_id);
