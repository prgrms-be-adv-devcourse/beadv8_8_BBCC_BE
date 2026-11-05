-- 자녀 계정 ↔ 자녀 프로필 연결 복사본. 인증의 child-account.changed로 채운다(#837)
-- 계정 ID는 다시 쓰지 않으므로 행은 "있음 → 삭제됨(deleted_at)" 한 방향으로만 바뀐다. 늦게 온 생성 메시지가 되살리지 못하게 삭제 행을 남긴다
CREATE TABLE childprofile.child_account_link (
    account_id bigint PRIMARY KEY,
    child_profile_id bigint NOT NULL,
    deleted_at timestamp(6),
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL
);
