-- 회원 탈퇴: 탈퇴하면 닉네임을 지우고 탈퇴 시각을 남긴다 (#457)
ALTER TABLE member.member ALTER COLUMN nickname DROP NOT NULL;
ALTER TABLE member.member ADD COLUMN withdrawn_at timestamp(6) without time zone;
