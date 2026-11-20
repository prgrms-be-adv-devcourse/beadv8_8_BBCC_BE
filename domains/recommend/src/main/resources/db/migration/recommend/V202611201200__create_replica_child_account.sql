-- 자녀 계정 → 자녀 프로필 · 보호자 복사본(child-account.changed, kidly.auth.events). 자녀 토큰으로 부른 추천을 그 계정의 자녀로 계산할 때 읽는다.
-- 계정 ID는 다시 쓰이지 않는다. 삭제는 한 방향(있음 → 삭제)이라 deleted_at이 한 번 차면 다시 비지 않는다.
-- 다른 도메인 값이라 FK는 두지 않는다.
CREATE TABLE recommend.replica_child_account (
    account_id bigint NOT NULL,
    child_profile_id bigint NOT NULL,
    guardian_id bigint NOT NULL,
    deleted_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT replica_child_account_pkey PRIMARY KEY (account_id)
);
