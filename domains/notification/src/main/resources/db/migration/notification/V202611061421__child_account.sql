-- 자녀 프로필 → 자녀 계정 복사본(계정당 1행). child-account.changed로 채운다
-- 조르기 이벤트에는 자녀 프로필 ID만 있으므로, 자녀 알림함(받는 사람 = 자녀 계정)에 넣을 때 프로필의 활성 계정을 찾는다
-- 계정마다 있음 → 삭제됨 한 방향으로만 바꾼다. 삭제를 먼저 받아도 행을 남겨, 늦게 온 생성이 되살리지 않게 한다
-- 보호자 ID 등 알림에 필요 없는 값은 두지 않는다
CREATE TABLE notification.child_account (
    account_id bigint NOT NULL,
    child_profile_id bigint NOT NULL,
    is_deleted boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT child_account_pkey PRIMARY KEY (account_id)
);

-- 프로필의 활성 계정 찾기
CREATE INDEX idx_child_account_child_profile_id_active
    ON notification.child_account (child_profile_id) WHERE is_deleted = false;
