CREATE POLICY pl_company_settings_tenant_isolation
    ON public.company_settings
    FOR ALL
    TO PUBLIC
    USING (
        company_id = NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id = NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );
