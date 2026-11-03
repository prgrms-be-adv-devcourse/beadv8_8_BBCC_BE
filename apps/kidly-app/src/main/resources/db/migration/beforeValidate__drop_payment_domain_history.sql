-- 결제 도메인 모듈의 마이그레이션(지갑 동결 V202611021530 · 잔액 환급 V202611021930)은 결제 서비스로 옮겨(#525) 이 앱의 클래스패스에 없다.
-- 이 앱이 적용한 이력 행만 남아 검증이 missing 으로 실패하므로 지운다. 이 앱 모듈에 있는 결제 파일(V202610161035 · V202610200643 · V202610300905)은
-- 이미 적용된 파일이라 그대로 두고 이 앱이 계속 가진다.
-- 서비스는 자기 이력 테이블(payment.flyway_schema_history_payment)을 쓰므로 이 행을 보지 않는다.
-- Flyway가 검증하기 전에 매번 실행된다. 이력 테이블이 없거나(새 DB) 이미 지웠으면 아무것도 하지 않는다.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        DELETE FROM public.flyway_schema_history
        WHERE script IN ('payment/V202611021530__wallet_frozen_at.sql', 'payment/V202611021930__balance_return.sql');
    END IF;
END
$$;
