CREATE TABLE company (
    company_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_company PRIMARY KEY (company_id),
    CONSTRAINT ck_company_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_company_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_company_version CHECK (version >= 0),
    CONSTRAINT ck_company_updated_at CHECK (updated_at >= created_at)
);

CREATE TABLE user_account (
    user_id UUID NOT NULL,
    company_id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_account PRIMARY KEY (user_id),
    CONSTRAINT uq_user_account_normalized_email UNIQUE (normalized_email),
    CONSTRAINT uq_user_account_user_company UNIQUE (user_id, company_id),
    CONSTRAINT fk_user_account_company
        FOREIGN KEY (company_id) REFERENCES company (company_id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_account_email_not_blank CHECK (CHAR_LENGTH(TRIM(email)) > 0),
    CONSTRAINT ck_user_account_normalized_email_not_blank
        CHECK (CHAR_LENGTH(TRIM(normalized_email)) > 0),
    CONSTRAINT ck_user_account_normalized_email
        CHECK (normalized_email = LOWER(TRIM(email))),
    CONSTRAINT ck_user_account_password_hash_not_blank
        CHECK (CHAR_LENGTH(TRIM(password_hash)) > 0),
    CONSTRAINT ck_user_account_role CHECK (role IN ('ADMIN', 'HR', 'VIEWER')),
    CONSTRAINT ck_user_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_user_account_version CHECK (version >= 0),
    CONSTRAINT ck_user_account_updated_at CHECK (updated_at >= created_at)
);

CREATE TABLE refresh_token (
    refresh_token_id UUID NOT NULL,
    user_id UUID NOT NULL,
    company_id UUID NOT NULL,
    token_family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    replaced_by_token_id UUID,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_refresh_token PRIMARY KEY (refresh_token_id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_refresh_token_id_family UNIQUE (refresh_token_id, token_family_id),
    CONSTRAINT uq_refresh_token_replaced_by UNIQUE (replaced_by_token_id),
    CONSTRAINT fk_refresh_token_user_company
        FOREIGN KEY (user_id, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_refresh_token_replacement_family
        FOREIGN KEY (replaced_by_token_id, token_family_id)
        REFERENCES refresh_token (refresh_token_id, token_family_id) ON DELETE RESTRICT,
    CONSTRAINT ck_refresh_token_hash_length CHECK (CHAR_LENGTH(token_hash) = 64),
    CONSTRAINT ck_refresh_token_hash_lowercase CHECK (token_hash = LOWER(token_hash)),
    CONSTRAINT ck_refresh_token_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_token_used_at CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT ck_refresh_token_revoked_at CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT ck_refresh_token_replacement
        CHECK (replaced_by_token_id IS NULL OR replaced_by_token_id <> refresh_token_id),
    CONSTRAINT ck_refresh_token_replacement_used
        CHECK (replaced_by_token_id IS NULL OR used_at IS NOT NULL),
    CONSTRAINT ck_refresh_token_updated_at CHECK (
        updated_at >= created_at
        AND (used_at IS NULL OR updated_at >= used_at)
        AND (revoked_at IS NULL OR updated_at >= revoked_at)
    ),
    CONSTRAINT ck_refresh_token_version CHECK (version >= 0)
);

CREATE INDEX idx_user_account_company ON user_account (company_id);
CREATE INDEX idx_refresh_token_company_user ON refresh_token (company_id, user_id);
CREATE INDEX idx_refresh_token_family_revoked ON refresh_token (token_family_id, revoked_at);
CREATE INDEX idx_refresh_token_expires_at ON refresh_token (expires_at);
