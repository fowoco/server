CREATE POLICY pl_worker_import_job_tenant_isolation
    ON public.worker_import_job
    FOR ALL
    TO PUBLIC
    USING (
        company_id = NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id = NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_worker_import_row_tenant_isolation
    ON public.worker_import_row
    FOR ALL
    TO PUBLIC
    USING (
        company_id = NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id = NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );

CREATE POLICY pl_worker_import_commit_idempotency_tenant_isolation
    ON public.worker_import_commit_idempotency
    FOR ALL
    TO PUBLIC
    USING (
        company_id = NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    )
    WITH CHECK (
        company_id = NULLIF(pg_catalog.current_setting('app.company_id', true), '')::UUID
    );
