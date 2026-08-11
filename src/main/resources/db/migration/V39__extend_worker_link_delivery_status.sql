ALTER TABLE worker_link
    DROP CONSTRAINT ck_worker_link_delivery_state;

ALTER TABLE worker_link
    DROP CONSTRAINT ck_worker_link_delivery_status;

ALTER TABLE worker_link
    ADD CONSTRAINT ck_worker_link_delivery_status
        CHECK (delivery_status IN ('NOT_SENT', 'SENDING', 'REVIEW_REQUIRED', 'SENT'));

ALTER TABLE worker_link
    ADD CONSTRAINT ck_worker_link_delivery_state
        CHECK (
            (delivery_status IN ('NOT_SENT', 'SENDING', 'REVIEW_REQUIRED')
                AND sent_at IS NULL AND sent_by IS NULL)
            OR (delivery_status = 'SENT' AND sent_at IS NOT NULL AND sent_by IS NOT NULL)
        );
