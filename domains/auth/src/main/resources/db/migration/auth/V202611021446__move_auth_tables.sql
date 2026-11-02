-- 소셜 계정 · Refresh Token 표를 회원 스키마에서 인증 스키마로 옮긴다. 소유 시퀀스 · 인덱스 · 제약이 함께 옮겨진다
ALTER TABLE member.social_account DROP CONSTRAINT fk_social_account_member;
ALTER TABLE member.social_account SET SCHEMA auth;
ALTER TABLE member.refresh_token SET SCHEMA auth;
ALTER TABLE auth.social_account ALTER COLUMN member_id DROP NOT NULL;
