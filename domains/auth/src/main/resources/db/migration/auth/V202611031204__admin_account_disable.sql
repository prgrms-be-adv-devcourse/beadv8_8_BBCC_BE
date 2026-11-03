ALTER TABLE auth.admin_account
    ADD COLUMN disabled_at timestamp,
    ADD COLUMN disabled_by bigint;
