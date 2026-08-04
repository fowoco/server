CREATE FUNCTION public.bootstrap_company_id_by_worker_link_token_hash(
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
    LIMIT 1
$$;

REVOKE ALL
    ON FUNCTION public.bootstrap_company_id_by_worker_link_token_hash(TEXT)
    FROM PUBLIC;
CREATE POLICY pl_worker_link_tenant_isolation
    ON public.worker_link
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

CREATE POLICY pl_worker_response_tenant_isolation
    ON public.worker_response
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

CREATE POLICY pl_worker_response_upload_tenant_isolation
    ON public.worker_response_upload
    FOR ALL
    TO PUBLIC
    USING (
        EXISTS (
            SELECT 1
            FROM public.worker_response AS response
            WHERE response.response_id = worker_response_upload.response_id
              AND response.company_id =
                  NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1
            FROM public.worker_response AS response
            WHERE response.response_id = worker_response_upload.response_id
              AND response.company_id =
                  NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
        )
    );