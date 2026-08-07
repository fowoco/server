ALTER TABLE worker
    ADD COLUMN visa_type VARCHAR(20);

ALTER TABLE worker
    ADD COLUMN employment_permit_end_date DATE;

ALTER TABLE worker
    ADD COLUMN employment_activity_end_date DATE;
