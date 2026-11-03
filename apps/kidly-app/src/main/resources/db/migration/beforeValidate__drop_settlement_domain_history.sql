-- 정산 아웃박스(V202610301630) · 유일 키(V202611021248) 마이그레이션은 정산 서비스로 옮겨(#729) 이 앱의 클래스패스에 없다. 이 앱이 적용한 이력 행만 남아 검증이 missing 으로 실패하므로 지운다.
-- 서비스는 자기 이력 테이블(settlement.flyway_schema_history_settlement)을 쓰므로 이 행을 보지 않는다.
-- Flyway가 검증하기 전에 매번 실행된다. 이력 테이블이 없거나(새 DB) 이미 지웠으면 아무것도 하지 않는다.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        DELETE FROM public.flyway_schema_history
        WHERE script IN ('settlement/V202610301630__settlement_outbox.sql',
                         'settlement/V202611021248__settlement_unique_seller_period_end_on.sql');
    END IF;
END
$$;
