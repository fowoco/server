CREATE POLICY pl_document_ocr_run_tenant_isolation
    ON public.document_ocr_run
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
