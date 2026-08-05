CREATE OR REPLACE FUNCTION public.bootstrap_company_id_by_worker_link_token_hash(
    p_token_hash TEXT
)
RETURNS UUID
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT link.company_id
    FROM public.worker_link AS link
    WHERE link.token_hash = p_token_hash
      AND link.status = 'ACTIVE'
      AND link.expires_at > CURRENT_TIMESTAMP
    LIMIT 1
$$;

REVOKE ALL
    ON FUNCTION public.bootstrap_company_id_by_worker_link_token_hash(TEXT)
    FROM PUBLIC;

ALTER POLICY pl_worker_response_upload_tenant_isolation
    ON public.worker_response_upload
    USING (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id =
        NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_worker_document_upload_idempotency_tenant_isolation
    ON public.worker_document_upload_idempotency
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
