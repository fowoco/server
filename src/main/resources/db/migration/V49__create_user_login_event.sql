CREATE TABLE user_login_event (
    login_event_id UUID NOT NULL,
    user_id UUID NOT NULL,
    company_id UUID NOT NULL,
    device_summary VARCHAR(120) NOT NULL,
    logged_in_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_login_event PRIMARY KEY (login_event_id),
    CONSTRAINT fk_user_login_event_user_company
        FOREIGN KEY (user_id, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_login_event_device_summary_not_blank
        CHECK (CHAR_LENGTH(TRIM(device_summary)) > 0)
);

CREATE INDEX idx_user_login_event_user_time
    ON user_login_event (user_id, company_id, logged_in_at DESC);
