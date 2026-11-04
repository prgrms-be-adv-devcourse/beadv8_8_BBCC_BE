-- 자녀 프로필 복사본. child.changed로 채우고 자녀 계정을 만들 때 보호자를 확인한다. 다른 스키마를 참조하지 않는다
CREATE TABLE auth.child_profile_replica (
    id          bigint       PRIMARY KEY,
    guardian_id bigint       NOT NULL,
    created_at  timestamp(6) NOT NULL,
    updated_at  timestamp(6) NOT NULL
);
