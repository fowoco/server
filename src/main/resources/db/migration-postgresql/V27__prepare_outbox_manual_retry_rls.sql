CREATE POLICY pl_outbox_manual_retry_tenant_isolation
    ON public.outbox_manual_retry
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
