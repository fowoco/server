CREATE FUNCTION public.bootstrap_company_id_by_normalized_email(
    p_normalized_email TEXT
)
RETURNS UUID
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT account.company_id
    FROM public.user_account AS account
    WHERE account.normalized_email = p_normalized_email
    LIMIT 1
$$;

CREATE FUNCTION public.bootstrap_company_id_by_refresh_token_hash(
    p_token_hash TEXT
)
RETURNS UUID
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT token.company_id
    FROM public.refresh_token AS token
    WHERE token.token_hash = p_token_hash
    LIMIT 1
$$;

CREATE FUNCTION public.bootstrap_claim_event_publications(
    p_owner TEXT,
    p_now TIMESTAMPTZ,
    p_lease_expires_at TIMESTAMPTZ,
    p_batch_size INTEGER,
    p_max_attempts INTEGER
)
RETURNS TABLE (
    event_id UUID,
    company_id UUID,
    review_required BOOLEAN
)
LANGUAGE SQL
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    WITH candidates AS (
        SELECT publication.event_id
        FROM public.event_publication AS publication
        WHERE p_owner IS NOT NULL
          AND CHAR_LENGTH(BTRIM(p_owner)) > 0
          AND p_now IS NOT NULL
          AND p_lease_expires_at > p_now
          AND p_batch_size > 0
          AND p_max_attempts >= 0
          AND (
              (
                  publication.status IN ('PENDING', 'RETRY_WAIT')
                  AND publication.next_attempt_at <= p_now
              )
              OR (
                  publication.status = 'PROCESSING'
                  AND publication.lease_expires_at <= p_now
              )
          )
        ORDER BY publication.occurred_at, publication.event_id
        FOR UPDATE SKIP LOCKED
        LIMIT p_batch_size
    ),
    claimed AS (
        UPDATE public.event_publication AS publication
        SET attempt_count = publication.attempt_count + 1,
            status = CASE
                WHEN publication.attempt_count + 1 > p_max_attempts
                    THEN 'REVIEW_REQUIRED'
                ELSE 'PROCESSING'
            END,
            next_attempt_at = NULL,
            lease_owner = CASE
                WHEN publication.attempt_count + 1 > p_max_attempts
                    THEN NULL
                ELSE p_owner
            END,
            lease_expires_at = CASE
                WHEN publication.attempt_count + 1 > p_max_attempts
                    THEN NULL
                ELSE p_lease_expires_at
            END,
            last_error_code = CASE
                WHEN publication.attempt_count + 1 > p_max_attempts
                    THEN 'EVENT_ATTEMPTS_EXHAUSTED'
                ELSE NULL
            END,
            updated_at = GREATEST(publication.updated_at, p_now),
            version = publication.version + 1
        FROM candidates
        WHERE publication.event_id = candidates.event_id
        RETURNING
            publication.event_id,
            publication.company_id,
            publication.status = 'REVIEW_REQUIRED' AS review_required
    )
    SELECT
        claimed.event_id,
        claimed.company_id,
        claimed.review_required
    FROM claimed
$$;

REVOKE ALL
    ON FUNCTION public.bootstrap_company_id_by_normalized_email(TEXT)
    FROM PUBLIC;
REVOKE ALL
    ON FUNCTION public.bootstrap_company_id_by_refresh_token_hash(TEXT)
    FROM PUBLIC;
REVOKE ALL
    ON FUNCTION public.bootstrap_claim_event_publications(
        TEXT,
        TIMESTAMPTZ,
        TIMESTAMPTZ,
        INTEGER,
        INTEGER
    )
    FROM PUBLIC;

CREATE POLICY pl_company_tenant_isolation
    ON public.company
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_user_account_tenant_isolation
    ON public.user_account
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_refresh_token_tenant_isolation
    ON public.refresh_token
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_worker_tenant_isolation
    ON public.worker
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_worker_document_tenant_isolation
    ON public.worker_document
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_task_tenant_isolation
    ON public.task
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_task_checklist_item_tenant_isolation
    ON public.task_checklist_item
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_task_transition_history_tenant_isolation
    ON public.task_transition_history
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_approval_request_tenant_isolation
    ON public.approval_request
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_external_submission_tenant_isolation
    ON public.external_submission
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_task_evidence_tenant_isolation
    ON public.task_evidence
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_audit_event_tenant_isolation
    ON public.audit_event
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_event_publication_tenant_isolation
    ON public.event_publication
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_event_consumption_tenant_isolation
    ON public.event_consumption
    FOR ALL
    TO PUBLIC
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );
