-- #906 자녀 계정 → 자녀 프로필 · 보호자 복사본(child-account.changed, kidly.auth.events). 조르기가 자녀 로그인(X-Child-*)을 프로필로 바꿀 때 읽는다.
-- 계정 ID는 다시 쓰이지 않는다. 삭제는 한 방향(있음 → 삭제)이라 deleted_at이 한 번 차면 다시 비지 않는다.
-- 시각 · 칸 이름은 다른 복사본(replica_seller · replica_child)과 같다. 다른 도메인 값이라 FK는 두지 않는다.
-- 주문 서비스 baseline 뒤 파일은 kidly-app 시절 DB에 이미 있을 수 있어 다시 적용돼도 안전하게 쓴다(OrderMigrationIntegrationTest).
-- 제약(PK)은 표 안에 두어 표와 함께 만들어지므로 IF NOT EXISTS 하나로 두 번 적용돼도 같다.
CREATE TABLE IF NOT EXISTS orders.replica_child_account (
    account_id bigint NOT NULL,
    child_profile_id bigint NOT NULL,
    guardian_id bigint NOT NULL,
    deleted_at timestamp(6) without time zone,
    event_occurred_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT replica_child_account_pkey PRIMARY KEY (account_id)
);
