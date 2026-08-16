ALTER TABLE worker_document_upload_idempotency
    ADD COLUMN idempotency_key_hash VARCHAR(64);

ALTER TABLE worker_document_upload_idempotency
    ADD COLUMN request_hash VARCHAR(64);

-- Existing rows and the previous application version keep using client_request_id only.
-- New rows written through the canonical Idempotency-Key flow populate both hashes.
ALTER TABLE worker_document_upload_idempotency
    ADD CONSTRAINT uq_worker_document_upload_idempotency_key_hash
        UNIQUE (worker_link_id, idempotency_key_hash);

ALTER TABLE worker_document_upload_idempotency
    ADD CONSTRAINT ck_worker_document_upload_idempotency_key_hash
        CHECK (
            idempotency_key_hash IS NULL
            OR CHAR_LENGTH(idempotency_key_hash) = 64
        );

ALTER TABLE worker_document_upload_idempotency
    ADD CONSTRAINT ck_worker_document_upload_idempotency_request_hash
        CHECK (
            request_hash IS NULL
            OR CHAR_LENGTH(request_hash) = 64
        );

ALTER TABLE worker_document_upload_idempotency
    ADD CONSTRAINT ck_worker_document_upload_idempotency_hash_pair
        CHECK (
            (idempotency_key_hash IS NULL AND request_hash IS NULL)
            OR (idempotency_key_hash IS NOT NULL AND request_hash IS NOT NULL)
        );
