ALTER TABLE worker_document
    DROP CONSTRAINT ck_worker_document_type;

ALTER TABLE worker_document
    ADD CONSTRAINT ck_worker_document_type
        CHECK (document_type IN (
            'PASSPORT_COPY',
            'ARC',
            'CONTRACT',
            'PERMIT',
            'EMPLOYMENT_EXTENSION_APPLICATION',
            'INTEGRATED_APPLICATION',
            'IDENTITY_GUARANTY',
            'RESIDENCE_PROOF'
        ));
