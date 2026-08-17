CREATE FUNCTION public.bootstrap_expired_stay_candidates(
    p_today DATE
)
RETURNS TABLE (
    company_id UUID,
    worker_id UUID,
    display_name VARCHAR(120),
    stay_expiry_date DATE
)
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT worker.company_id,
           worker.worker_id,
           worker.display_name,
           worker.stay_expiry_date
      FROM public.worker AS worker
     WHERE worker.stay_expiry_date < p_today
       AND worker.work_status IN ('ACTIVE', 'ON_LEAVE')
       AND NOT EXISTS (
           SELECT 1
             FROM public.stay_verification_case AS verification
            WHERE verification.company_id = worker.company_id
              AND verification.worker_id = worker.worker_id
              AND verification.source_stay_expiry_date = worker.stay_expiry_date
       )
     ORDER BY worker.company_id, worker.worker_id
$$;

REVOKE ALL
    ON FUNCTION public.bootstrap_expired_stay_candidates(DATE)
    FROM PUBLIC;

CREATE POLICY pl_stay_verification_tenant_isolation
    ON public.stay_verification_case
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
