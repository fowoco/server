ALTER TABLE user_account
    ADD COLUMN password_changed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE user_account SET password_changed_at = created_at;
