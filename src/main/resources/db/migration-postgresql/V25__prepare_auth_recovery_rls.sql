CREATE FUNCTION public.bootstrap_company_id_by_password_reset_token_hash(
    p_token_hash TEXT
)
RETURNS UUID
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT token.company_id
    FROM public.password_reset_token AS token
    WHERE token.token_hash = p_token_hash
      AND token.used_at IS NULL
      AND token.expires_at > CURRENT_TIMESTAMP
    LIMIT 1
$$;

REVOKE ALL
    ON FUNCTION public.bootstrap_company_id_by_password_reset_token_hash(TEXT)
    FROM PUBLIC;

CREATE POLICY pl_user_agreement_consent_tenant_isolation
    ON public.user_agreement_consent
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

CREATE POLICY pl_password_reset_token_tenant_isolation
    ON public.password_reset_token
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
