-- 회원 · 인증 마이그레이션(member/ · auth/)은 회원 서비스로 옮겨(#769) 이 앱의 클래스패스에 없다. 이 앱이 적용한 이력 행만 남아 검증이 missing 으로 실패하므로 지운다.
-- 서비스는 자기 이력 테이블(member.flyway_schema_history_member)을 쓰므로 이 행을 보지 않는다.
-- Flyway가 검증하기 전에 매번 실행된다. 이력 테이블이 없거나(새 DB) 이미 지웠으면 아무것도 하지 않는다.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        DELETE FROM public.flyway_schema_history
        WHERE script LIKE 'member/%' OR script LIKE 'auth/%';
    END IF;
END
$$;
