ALTER TABLE user_account
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE user_account
    ADD COLUMN locked_until TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE user_account
    ADD COLUMN last_failed_login_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_failed_login_attempts
        CHECK (failed_login_attempts >= 0);
