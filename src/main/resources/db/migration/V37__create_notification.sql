CREATE TABLE notification (
    notification_id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id UUID NOT NULL,
    route VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notification_company_read_occurred
    ON notification (company_id, is_read, occurred_at DESC);
