ALTER TABLE user_account
    ADD COLUMN display_name VARCHAR(80) NOT NULL DEFAULT '사용자';

ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_display_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(display_name)) > 0);
