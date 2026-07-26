-- purpose: #7(Worker Link)도 FileStorage를 재사용할 예정이라 enum 값이
-- 여러 이슈에 걸쳐 공유됨. 확정 전까지 CHECK 제약 없이 자유 문자열로 둔다.
CREATE TABLE stored_file (
    stored_file_id UUID NOT NULL,
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(127) NOT NULL,
    size BIGINT NOT NULL,
    purpose VARCHAR(60) NOT NULL,
    task_id UUID,
    worker_id UUID,
    storage_key VARCHAR(255) NOT NULL,
    scan_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stored_file PRIMARY KEY (stored_file_id),
    CONSTRAINT uq_stored_file_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_stored_file_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stored_file_task_company
        FOREIGN KEY (task_id, company_id)
        REFERENCES task (task_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stored_file_worker_company
        FOREIGN KEY (worker_id, company_id)
        REFERENCES worker (worker_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_stored_file_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_stored_file_mime_type_not_blank CHECK (CHAR_LENGTH(TRIM(mime_type)) > 0),
    CONSTRAINT ck_stored_file_purpose_not_blank CHECK (CHAR_LENGTH(TRIM(purpose)) > 0),
    CONSTRAINT ck_stored_file_size_positive CHECK (size > 0),
    CONSTRAINT ck_stored_file_scan_status
        CHECK (scan_status IN ('NOT_SCANNED'))
);

CREATE INDEX idx_stored_file_company ON stored_file (company_id);
CREATE INDEX idx_stored_file_company_task ON stored_file (company_id, task_id);
CREATE INDEX idx_stored_file_company_worker ON stored_file (company_id, worker_id);
