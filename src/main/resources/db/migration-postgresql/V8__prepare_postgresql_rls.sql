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
    p_lease_duration_millis BIGINT,
    p_batch_size INTEGER,
    p_max_attempts INTEGER
)
RETURNS TABLE (
    event_id UUID,
    company_id UUID,
    review_required BOOLEAN
)
LANGUAGE PLPGSQL
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_owner TEXT;
    v_now TIMESTAMPTZ;
    v_lease_expires_at TIMESTAMPTZ;
BEGIN
    v_owner := BTRIM(p_owner);
    IF v_owner IS NULL OR CHAR_LENGTH(v_owner) NOT BETWEEN 1 AND 128 THEN
        RAISE EXCEPTION 'Outbox claim owner must be 1 to 128 characters.'
            USING ERRCODE = '22023';
    END IF;
    IF p_lease_duration_millis IS NULL
        OR p_lease_duration_millis NOT BETWEEN 1 AND 86400000 THEN
        RAISE EXCEPTION 'Outbox claim lease duration must be between 1 millisecond and 1 day.'
            USING ERRCODE = '22023';
    END IF;
    IF p_batch_size IS NULL OR p_batch_size NOT BETWEEN 1 AND 500 THEN
        RAISE EXCEPTION 'Outbox claim batch size must be between 1 and 500.'
            USING ERRCODE = '22023';
    END IF;
    IF p_max_attempts IS NULL OR p_max_attempts NOT BETWEEN 1 AND 100 THEN
        RAISE EXCEPTION 'Outbox claim max attempts must be between 1 and 100.'
            USING ERRCODE = '22023';
    END IF;

    v_now := statement_timestamp();
    v_lease_expires_at :=
        v_now + p_lease_duration_millis * INTERVAL '1 millisecond';

    RETURN QUERY
    WITH candidates AS (
        SELECT publication.event_id
        FROM public.event_publication AS publication
        WHERE (
              (
                  publication.status IN ('PENDING', 'RETRY_WAIT')
                  AND publication.next_attempt_at <= v_now
              )
              OR (
                  publication.status = 'PROCESSING'
                  AND publication.lease_expires_at <= v_now
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
                ELSE v_owner
            END,
            lease_expires_at = CASE
                WHEN publication.attempt_count + 1 > p_max_attempts
                    THEN NULL
                ELSE v_lease_expires_at
            END,
            last_error_code = CASE
                WHEN publication.attempt_count + 1 > p_max_attempts
                    THEN 'EVENT_ATTEMPTS_EXHAUSTED'
                ELSE NULL
            END,
            updated_at = GREATEST(publication.updated_at, v_now),
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
    ;
END;
$$;

CREATE FUNCTION public.bootstrap_count_outstanding_event_publications()
RETURNS BIGINT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT COUNT(*)
    FROM public.event_publication AS publication
    WHERE publication.status IN (
        'PENDING',
        'PROCESSING',
        'RETRY_WAIT',
        'REVIEW_REQUIRED'
    )
$$;

CREATE FUNCTION public.bootstrap_oldest_outstanding_event_occurred_at()
RETURNS TIMESTAMPTZ
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT MIN(publication.occurred_at)
    FROM public.event_publication AS publication
    WHERE publication.status IN (
        'PENDING',
        'PROCESSING',
        'RETRY_WAIT',
        'REVIEW_REQUIRED'
    )
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
        BIGINT,
        INTEGER,
        INTEGER
    )
    FROM PUBLIC;
REVOKE ALL
    ON FUNCTION public.bootstrap_count_outstanding_event_publications()
    FROM PUBLIC;
REVOKE ALL
    ON FUNCTION public.bootstrap_oldest_outstanding_event_occurred_at()
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
