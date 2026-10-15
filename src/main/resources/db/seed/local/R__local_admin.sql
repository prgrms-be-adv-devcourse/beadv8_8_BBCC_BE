-- 로컬 전용 관리자 계정. 로컬은 카카오 로그인이 안 되므로 소셜 계정 없이 만들고,
-- POST /api/v1/dev/tokens 에 이 회원의 id와 roles ["ADMIN"]을 보내 토큰을 받는다
INSERT INTO member.member (nickname, role, status, created_at, updated_at)
VALUES ('관리자', 'ADMIN', 'ACTIVE', now(), now())
ON CONFLICT (nickname) DO UPDATE SET role = 'ADMIN', status = 'ACTIVE', updated_at = now();
