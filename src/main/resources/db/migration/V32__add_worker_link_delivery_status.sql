ALTER TABLE worker_link
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SENT';

ALTER TABLE worker_link
    ADD COLUMN sent_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE worker_link
    ADD COLUMN sent_by UUID;

ALTER TABLE worker_link
    ADD CONSTRAINT fk_worker_link_sent_by_company
        FOREIGN KEY (sent_by, company_id)
        REFERENCES user_account (user_id, company_id) ON DELETE RESTRICT;

ALTER TABLE worker_link
    ADD CONSTRAINT ck_worker_link_delivery_status
        CHECK (delivery_status IN ('NOT_SENT', 'SENT'));

ALTER TABLE worker_link
    ADD CONSTRAINT ck_worker_link_delivery_state
        CHECK (
            (delivery_status = 'NOT_SENT' AND sent_at IS NULL AND sent_by IS NULL)
            OR (delivery_status = 'SENT' AND sent_at IS NOT NULL AND sent_by IS NOT NULL)
        );
