CREATE TABLE notification_preference (
    notification_preference_id UUID NOT NULL,
    user_id UUID NOT NULL,
    company_id UUID NOT NULL,
    pref_key VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notification_preference PRIMARY KEY (notification_preference_id),
    CONSTRAINT uq_notification_preference_user_key UNIQUE (user_id, company_id, pref_key),
    CONSTRAINT fk_notification_preference_user_company
        FOREIGN KEY (user_id, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_preference_key_not_blank CHECK (CHAR_LENGTH(TRIM(pref_key)) > 0)
);

CREATE INDEX idx_notification_preference_user
    ON notification_preference (user_id, company_id);
