ALTER TABLE task
    ADD COLUMN target_type VARCHAR(20) NOT NULL DEFAULT 'WORKER';

ALTER TABLE task
    ALTER COLUMN worker_id DROP NOT NULL;

ALTER TABLE task
    ALTER COLUMN case_id DROP NOT NULL;

ALTER TABLE task
    DROP CONSTRAINT ck_task_type;

ALTER TABLE task
    ADD CONSTRAINT ck_task_type CHECK (
        task_type IN (
            'RECONTRACT',
            'EMPLOYMENT_PERIOD_EXTENSION',
            'STAY_PERIOD_EXTENSION',
            'DOCUMENT_REQUEST',
            'WORKER_ONBOARDING',
            'PAYROLL_EXPLANATION',
            'EMPLOYMENT_CHANGE',
            'WORK_INSTRUCTION'
        )
    );

ALTER TABLE task
    DROP CONSTRAINT ck_task_source;

ALTER TABLE task
    ADD CONSTRAINT ck_task_source CHECK (
        source IN (
            'MANUAL',
            'SYSTEM_DDAY',
            'AI_CANDIDATE',
            'FILE_IMPORT',
            'WORKER_RESPONSE'
        )
    );

ALTER TABLE task
    ADD CONSTRAINT ck_task_target CHECK (
        (target_type = 'WORKER' AND worker_id IS NOT NULL AND case_id IS NOT NULL)
        OR (target_type = 'COMPANY' AND worker_id IS NULL AND case_id IS NULL)
    );

CREATE INDEX idx_task_company_target_source
    ON task (company_id, target_type, source, created_at DESC);
