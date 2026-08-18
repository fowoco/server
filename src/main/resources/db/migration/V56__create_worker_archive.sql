CREATE TABLE worker_archive (
    worker_id UUID NOT NULL,
    company_id UUID NOT NULL,
    archived_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    archived_by UUID NOT NULL,
    archive_reason VARCHAR(500) NOT NULL,
    worker_version BIGINT NOT NULL,
    CONSTRAINT pk_worker_archive PRIMARY KEY (worker_id),
    CONSTRAINT uq_worker_archive_worker_company UNIQUE (worker_id, company_id),
    CONSTRAINT fk_worker_archive_worker_company
        FOREIGN KEY (worker_id, company_id)
        REFERENCES worker (worker_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_worker_archive_actor_company
        FOREIGN KEY (archived_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_worker_archive_reason_not_blank
        CHECK (CHAR_LENGTH(TRIM(archive_reason)) > 0),
    CONSTRAINT ck_worker_archive_worker_version CHECK (worker_version > 0)
);

CREATE INDEX idx_worker_archive_company_time
    ON worker_archive (company_id, archived_at DESC, worker_id);
