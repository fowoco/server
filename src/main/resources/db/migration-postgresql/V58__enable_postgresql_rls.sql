SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE public.company ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.company_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.refresh_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_agreement_consent ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.password_reset_token ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.worker ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.worker_document ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stored_file ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.task ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.task_checklist_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.task_transition_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.document_request_draft ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.document_request_draft_type ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.approval_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.external_submission ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.task_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workflow_case ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.document_ocr_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stay_verification_case ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.worker_archive ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.worker_link ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.worker_response ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.worker_response_upload ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.worker_document_upload_idempotency ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.worker_import_job ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.worker_import_row ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.worker_import_commit_idempotency ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.ai_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_attempt ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_question ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_candidate ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_candidate_decision_batch ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_candidate_decision ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_candidate_decision_task ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.event_publication ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.event_consumption ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.outbox_manual_retry ENABLE ROW LEVEL SECURITY;
