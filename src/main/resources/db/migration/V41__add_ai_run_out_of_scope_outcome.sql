ALTER TABLE ai_run
    DROP CONSTRAINT ck_ai_run_outcome;

ALTER TABLE ai_run
    ADD CONSTRAINT ck_ai_run_outcome CHECK (
        analysis_outcome IS NULL OR analysis_outcome IN (
            'OUT_OF_SCOPE', 'CONTEXT_REQUIRED', 'NEEDS_INFO', 'REVIEW_REQUIRED'
        )
    );
