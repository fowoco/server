CREATE TABLE company_settings (
    company_id UUID NOT NULL,
    approval_policy VARCHAR(30) NOT NULL DEFAULT 'ADMIN_OR_HR',
    link_expiry_hours BIGINT NOT NULL DEFAULT 72,
    evidence_rules_json TEXT NOT NULL DEFAULT '{}',
    file_retention_days INTEGER NOT NULL DEFAULT 365,
    ai_log_retention_days INTEGER NOT NULL DEFAULT 90,
    audit_visibility VARCHAR(30) NOT NULL DEFAULT 'ADMIN_ONLY',
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_company_settings PRIMARY KEY (company_id),
    CONSTRAINT fk_company_settings_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE CASCADE,
    CONSTRAINT ck_company_settings_approval_policy
        CHECK (approval_policy IN ('ADMIN_ONLY', 'ADMIN_OR_HR')),
    CONSTRAINT ck_company_settings_link_expiry_hours
        CHECK (link_expiry_hours BETWEEN 1 AND 168),
    CONSTRAINT ck_company_settings_evidence_rules_json_not_blank
        CHECK (CHAR_LENGTH(TRIM(evidence_rules_json)) > 0),
    CONSTRAINT ck_company_settings_file_retention_days
        CHECK (file_retention_days BETWEEN 30 AND 3650),
    CONSTRAINT ck_company_settings_ai_log_retention_days
        CHECK (ai_log_retention_days BETWEEN 7 AND 365),
    CONSTRAINT ck_company_settings_audit_visibility
        CHECK (audit_visibility IN ('ADMIN_ONLY', 'ADMIN_AND_HR')),
    CONSTRAINT ck_company_settings_version CHECK (version >= 0),
    CONSTRAINT ck_company_settings_time_order CHECK (updated_at >= created_at)
);

INSERT INTO company_settings (company_id)
SELECT company_id
FROM company;
