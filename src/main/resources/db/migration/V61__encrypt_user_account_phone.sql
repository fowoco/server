ALTER TABLE user_account
    ADD COLUMN phone_ciphertext TEXT;

ALTER TABLE user_account
    ADD COLUMN phone_key_version VARCHAR(60);

ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_phone_cipher_pair
        CHECK (
            (phone_ciphertext IS NULL AND phone_key_version IS NULL)
            OR
            (phone_ciphertext IS NOT NULL AND phone_key_version IS NOT NULL)
        );

ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_phone_single_storage
        CHECK (phone IS NULL OR phone_ciphertext IS NULL);
