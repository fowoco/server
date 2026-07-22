CREATE TABLE company (
    company_name uuid NOT NULL,
    name text NOT NULL,
    CONSTRAINT pk_company PRIMARY KEY (company_name)
);

CREATE TABLE user_account (
    user_id text NOT NULL,
    company_name uuid NOT NULL,
    role text NOT NULL,
    id text NOT NULL,
    password text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_account PRIMARY KEY (user_id),
    CONSTRAINT fk_user_account_company
        FOREIGN KEY (company_name)
        REFERENCES company (company_name)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT uq_user_account_id UNIQUE (id),
    CONSTRAINT ck_user_account_role
        CHECK (role IN ('OWNER', 'HR_MANAGER', 'HR_STAFF'))
);

CREATE TABLE worker (
    worker_id text NOT NULL,
    legal_name_enc bytea NOT NULL,
    company_name uuid NOT NULL,
    employee_no text NOT NULL,
    nationality text NOT NULL,
    visa_type text NOT NULL,
    stay_expiry date NOT NULL,
    contract_start date NOT NULL,
    contract_end date NOT NULL,
    phone bytea NULL,
    email text NULL,
    arrival_date date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_worker PRIMARY KEY (worker_id),
    CONSTRAINT fk_worker_company
        FOREIGN KEY (company_name)
        REFERENCES company (company_name)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT ck_worker_contract_dates
        CHECK (contract_end >= contract_start)
);

CREATE TABLE task (
    task_id text NOT NULL,
    company_name uuid NOT NULL,
    task_type text NOT NULL,
    due_date date NULL,
    status text NOT NULL,
    source_rule text NULL,
    assignee_id text NULL,
    completed_by text NULL,
    completed_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_task PRIMARY KEY (task_id),
    CONSTRAINT fk_task_company
        FOREIGN KEY (company_name)
        REFERENCES company (company_name)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_task_assignee
        FOREIGN KEY (assignee_id)
        REFERENCES user_account (user_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_task_completed_by
        FOREIGN KEY (completed_by)
        REFERENCES user_account (user_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

CREATE TABLE ticket (
    ticket_id uuid NOT NULL,
    task_id text NOT NULL,
    worker_id text NULL,
    assignee_id text NULL,
    status text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    messages_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    link_token_hash bytea NULL,
    link_status text NULL,
    link_issued_by text NULL,
    link_delivery_channel text NULL,
    link_expires_at timestamptz NULL,
    link_replaces_hash bytea NULL,
    CONSTRAINT pk_ticket PRIMARY KEY (ticket_id),
    CONSTRAINT fk_ticket_task
        FOREIGN KEY (task_id)
        REFERENCES task (task_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_ticket_worker
        FOREIGN KEY (worker_id)
        REFERENCES worker (worker_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_ticket_assignee
        FOREIGN KEY (assignee_id)
        REFERENCES user_account (user_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_ticket_link_issued_by
        FOREIGN KEY (link_issued_by)
        REFERENCES user_account (user_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT uq_ticket_link_token_hash UNIQUE (link_token_hash),
    CONSTRAINT ck_ticket_messages_json_array
        CHECK (jsonb_typeof(messages_json) = 'array')
);

CREATE TABLE document (
    document_id uuid NOT NULL,
    worker_id text NULL,
    task_id text NULL,
    ticket_id uuid NULL,
    doc_type text NOT NULL,
    status text NOT NULL,
    evidence_type text NULL,
    due_date date NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_uri_enc bytea NULL,
    value_enc bytea NULL,
    verified_by text NULL,
    verified_at timestamptz NULL,
    CONSTRAINT pk_document PRIMARY KEY (document_id),
    CONSTRAINT fk_document_worker
        FOREIGN KEY (worker_id)
        REFERENCES worker (worker_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_document_task
        FOREIGN KEY (task_id)
        REFERENCES task (task_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_document_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES ticket (ticket_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_document_verified_by
        FOREIGN KEY (verified_by)
        REFERENCES user_account (user_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT ck_document_has_tenant_link
        CHECK (worker_id IS NOT NULL OR task_id IS NOT NULL OR ticket_id IS NOT NULL)
);

CREATE INDEX idx_user_account_company_name ON user_account (company_name);

CREATE INDEX idx_worker_company_name ON worker (company_name);

CREATE INDEX idx_task_company_name ON task (company_name);
CREATE INDEX idx_task_assignee_id ON task (assignee_id);
CREATE INDEX idx_task_completed_by ON task (completed_by);

CREATE INDEX idx_ticket_task_id ON ticket (task_id);
CREATE INDEX idx_ticket_worker_id ON ticket (worker_id);
CREATE INDEX idx_ticket_assignee_id ON ticket (assignee_id);
CREATE INDEX idx_ticket_link_issued_by ON ticket (link_issued_by);

CREATE INDEX idx_document_worker_id ON document (worker_id);
CREATE INDEX idx_document_task_id ON document (task_id);
CREATE INDEX idx_document_ticket_id ON document (ticket_id);
CREATE INDEX idx_document_verified_by ON document (verified_by);

COMMENT ON COLUMN company.company_name IS
    'Stable company UUID identifier; the display name is stored in company.name.';
COMMENT ON COLUMN user_account.id IS
    'Unique login identifier. FOWOCO currently stores an email-shaped value such as test@test.com.';
COMMENT ON COLUMN user_account.password IS
    'One-way Argon2id or bcrypt password hash only; never a plaintext password.';
COMMENT ON COLUMN worker.worker_id IS
    'Stable application-generated text identifier; never a legal name or other mutable personal value.';
COMMENT ON COLUMN worker.legal_name_enc IS
    'Application/KMS-encrypted legal name bytes; no encryption key is stored in the database.';
COMMENT ON COLUMN worker.phone IS
    'Application/KMS-encrypted phone bytes; NULL when no phone is retained.';
COMMENT ON COLUMN ticket.messages_json IS
    'Conversation message array. Concurrent changes require an atomic UPDATE or row lock in the application.';
COMMENT ON COLUMN ticket.link_token_hash IS
    'One-way hash of the expiring link token; the original token is never stored.';
COMMENT ON COLUMN document.file_uri_enc IS
    'Application/KMS-encrypted S3 object key; never a full URL or file content.';
COMMENT ON COLUMN document.value_enc IS
    'Application/KMS-encrypted evidence value such as a receipt number.';
-- Tenant context must be set for every transaction, for example:
--   SET LOCAL app.company_name = '00000000-0000-0000-0000-000000000001';
-- NULLIF also fails closed after a transaction-local setting has been reset to
-- an empty value. A malformed non-empty UUID raises an error rather than
-- bypassing isolation.
ALTER TABLE company ENABLE ROW LEVEL SECURITY;
ALTER TABLE company FORCE ROW LEVEL SECURITY;
ALTER TABLE user_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_account FORCE ROW LEVEL SECURITY;
ALTER TABLE worker ENABLE ROW LEVEL SECURITY;
ALTER TABLE worker FORCE ROW LEVEL SECURITY;
ALTER TABLE task ENABLE ROW LEVEL SECURITY;
ALTER TABLE task FORCE ROW LEVEL SECURITY;
ALTER TABLE ticket ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket FORCE ROW LEVEL SECURITY;
ALTER TABLE document ENABLE ROW LEVEL SECURITY;
ALTER TABLE document FORCE ROW LEVEL SECURITY;
CREATE POLICY pol_company_tenant_all
    ON company
    FOR ALL
    TO PUBLIC
    USING (
        company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
    )
    WITH CHECK (
        company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
    );

CREATE POLICY pol_user_account_tenant_all
    ON user_account
    FOR ALL
    TO PUBLIC
    USING (
        company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
    )
    WITH CHECK (
        company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
    );

CREATE POLICY pol_worker_tenant_all
    ON worker
    FOR ALL
    TO PUBLIC
    USING (
        company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
    )
    WITH CHECK (
        company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
    );

CREATE POLICY pol_task_tenant_all
    ON task
    FOR ALL
    TO PUBLIC
    USING (
        task.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
        AND (
            task.assignee_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS assignee
                WHERE assignee.user_id = task.assignee_id
                  AND assignee.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            task.completed_by IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS completer
                WHERE completer.user_id = task.completed_by
                  AND completer.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
    )
    WITH CHECK (
        task.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
        AND (
            task.assignee_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS assignee
                WHERE assignee.user_id = task.assignee_id
                  AND assignee.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            task.completed_by IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS completer
                WHERE completer.user_id = task.completed_by
                  AND completer.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
    );

CREATE POLICY pol_ticket_tenant_all
    ON ticket
    FOR ALL
    TO PUBLIC
    USING (
        EXISTS (
            SELECT 1
            FROM task AS owning_task
            WHERE owning_task.task_id = ticket.task_id
              AND owning_task.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
        )
        AND (
            ticket.worker_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM worker AS linked_worker
                WHERE linked_worker.worker_id = ticket.worker_id
                  AND linked_worker.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            ticket.assignee_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS assignee
                WHERE assignee.user_id = ticket.assignee_id
                  AND assignee.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            ticket.link_issued_by IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS issuer
                WHERE issuer.user_id = ticket.link_issued_by
                  AND issuer.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1
            FROM task AS owning_task
            WHERE owning_task.task_id = ticket.task_id
              AND owning_task.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
        )
        AND (
            ticket.worker_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM worker AS linked_worker
                WHERE linked_worker.worker_id = ticket.worker_id
                  AND linked_worker.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            ticket.assignee_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS assignee
                WHERE assignee.user_id = ticket.assignee_id
                  AND assignee.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            ticket.link_issued_by IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS issuer
                WHERE issuer.user_id = ticket.link_issued_by
                  AND issuer.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
    );

CREATE POLICY pol_document_tenant_all
    ON document
    FOR ALL
    TO PUBLIC
    USING (
        (document.worker_id IS NOT NULL OR document.task_id IS NOT NULL OR document.ticket_id IS NOT NULL)
        AND (
            document.worker_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM worker AS linked_worker
                WHERE linked_worker.worker_id = document.worker_id
                  AND linked_worker.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            document.task_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM task AS linked_task
                WHERE linked_task.task_id = document.task_id
                  AND linked_task.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            document.ticket_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM ticket AS linked_ticket
                JOIN task AS ticket_task
                  ON ticket_task.task_id = linked_ticket.task_id
                WHERE linked_ticket.ticket_id = document.ticket_id
                  AND ticket_task.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            document.verified_by IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS verifier
                WHERE verifier.user_id = document.verified_by
                  AND verifier.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
    )
    WITH CHECK (
        (document.worker_id IS NOT NULL OR document.task_id IS NOT NULL OR document.ticket_id IS NOT NULL)
        AND (
            document.worker_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM worker AS linked_worker
                WHERE linked_worker.worker_id = document.worker_id
                  AND linked_worker.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            document.task_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM task AS linked_task
                WHERE linked_task.task_id = document.task_id
                  AND linked_task.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            document.ticket_id IS NULL
            OR EXISTS (
                SELECT 1
                FROM ticket AS linked_ticket
                JOIN task AS ticket_task
                  ON ticket_task.task_id = linked_ticket.task_id
                WHERE linked_ticket.ticket_id = document.ticket_id
                  AND ticket_task.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
        AND (
            document.verified_by IS NULL
            OR EXISTS (
                SELECT 1
                FROM user_account AS verifier
                WHERE verifier.user_id = document.verified_by
                  AND verifier.company_name = NULLIF(current_setting('app.company_name', true), '')::uuid
            )
        )
    );
