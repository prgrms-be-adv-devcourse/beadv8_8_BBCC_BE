-- Refresh Token 키를 회원 ID에서 (주체 종류, 주체 ID)로 바꾼다. 있던 행은 모두 회원이다
ALTER TABLE auth.refresh_token RENAME COLUMN member_id TO principal_id;
ALTER TABLE auth.refresh_token ADD COLUMN principal_type varchar(20) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE auth.refresh_token ALTER COLUMN principal_type DROP DEFAULT;
ALTER TABLE auth.refresh_token
    ADD CONSTRAINT refresh_token_principal_type_check CHECK (principal_type IN ('MEMBER', 'ADMIN'));
ALTER TABLE auth.refresh_token DROP CONSTRAINT refresh_token_pkey;
ALTER TABLE auth.refresh_token ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (principal_type, principal_id);
