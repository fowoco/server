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
