CREATE TABLE user_agreement_consent (
    consent_id UUID NOT NULL,
    company_id UUID NOT NULL,
    user_id UUID NOT NULL,
    agreement_type VARCHAR(30) NOT NULL,
    policy_version VARCHAR(40) NOT NULL,
    agreed BOOLEAN NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    recorded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_agreement_consent PRIMARY KEY (consent_id),
    CONSTRAINT uq_user_agreement_consent_id_company UNIQUE (consent_id, company_id),
    CONSTRAINT fk_user_agreement_consent_user_company
        FOREIGN KEY (user_id, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_agreement_consent_type CHECK (
        agreement_type IN ('SERVICE_TERMS', 'PRIVACY_POLICY', 'MARKETING')
    ),
    CONSTRAINT ck_user_agreement_consent_version_not_blank
        CHECK (CHAR_LENGTH(TRIM(policy_version)) > 0),
    CONSTRAINT ck_user_agreement_consent_request_not_blank
        CHECK (CHAR_LENGTH(TRIM(request_id)) > 0)
);

CREATE TABLE password_reset_token (
    password_reset_token_id UUID NOT NULL,
    company_id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_password_reset_token PRIMARY KEY (password_reset_token_id),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_password_reset_token_id_company
        UNIQUE (password_reset_token_id, company_id),
    CONSTRAINT fk_password_reset_token_user_company
        FOREIGN KEY (user_id, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_password_reset_token_hash_length CHECK (CHAR_LENGTH(token_hash) = 64),
    CONSTRAINT ck_password_reset_token_hash_lowercase CHECK (token_hash = LOWER(token_hash)),
    CONSTRAINT ck_password_reset_token_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_password_reset_token_used_at CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT ck_password_reset_token_updated_at CHECK (
        updated_at >= created_at
        AND (used_at IS NULL OR updated_at >= used_at)
    ),
    CONSTRAINT ck_password_reset_token_version CHECK (version >= 0)
);

CREATE INDEX idx_user_agreement_consent_user_time
    ON user_agreement_consent (company_id, user_id, agreement_type, recorded_at);
CREATE INDEX idx_password_reset_token_company_user
    ON password_reset_token (company_id, user_id, created_at);
CREATE INDEX idx_password_reset_token_active
    ON password_reset_token (company_id, user_id, used_at, expires_at);
