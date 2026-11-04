ALTER TABLE auth.refresh_token DROP CONSTRAINT refresh_token_principal_type_check;
ALTER TABLE auth.refresh_token ADD CONSTRAINT refresh_token_principal_type_check
    CHECK (principal_type IN ('MEMBER', 'ADMIN', 'CHILD'));
