ALTER TABLE worker_response
    ADD COLUMN answers_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE worker_response
    ADD COLUMN request_fingerprint VARCHAR(64);

ALTER TABLE worker_response
    DROP CONSTRAINT ck_worker_response_type;

ALTER TABLE worker_response
    ADD CONSTRAINT ck_worker_response_type
        CHECK (response_type IN (
            'ACKNOWLEDGED',
            'QUESTION',
            'NOT_UNDERSTOOD',
            'DOCUMENT_SUBMITTED',
            'DIFFICULT',
            'SLOT_ANSWERS_SUBMITTED'
        ));

ALTER TABLE worker_response
    ADD CONSTRAINT ck_worker_response_answers_json_not_blank
        CHECK (CHAR_LENGTH(TRIM(answers_json)) > 0);

ALTER TABLE worker_response
    ADD CONSTRAINT ck_worker_response_request_fingerprint
        CHECK (
            request_fingerprint IS NULL
            OR (
                CHAR_LENGTH(request_fingerprint) = 64
                AND request_fingerprint = LOWER(request_fingerprint)
            )
        );
