-- 자녀 아웃박스(V202610300953)는 자녀 프로필 서비스로 옮겨(#682) 이 앱의 클래스패스에 없다. 이 앱이 적용한 이력 행만 남아 검증이 missing 으로 실패하므로 지운다.
-- 서비스는 자기 이력 테이블(childprofile.flyway_schema_history_childprofile)을 쓰므로 이 행을 보지 않는다.
-- Flyway가 검증하기 전에 매번 실행된다. 이력 테이블이 없거나(새 DB) 이미 지웠으면 아무것도 하지 않는다.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        DELETE FROM public.flyway_schema_history
        WHERE script = 'childprofile/V202610300953__child_profile_outbox.sql';
    END IF;
END
$$;
