-- 상품 도메인 모듈의 마이그레이션(아웃박스 V202610292246 · 검색 문서 삭제 V202611040911 · 아웃박스 보강 V202611041223)은 상품 서비스로 옮겨(#791) 이 앱의 클래스패스에 없다.
-- 이 앱이 적용한 이력 행만 남아 검증이 missing 으로 실패하므로 지운다. 이 앱 모듈에 있는 상품 파일(init · V202610181146)은
-- 이미 적용된 파일이라 그대로 두고 이 앱이 계속 가진다.
-- 서비스는 자기 이력 테이블(product.flyway_schema_history_product)을 쓰므로 이 행을 보지 않는다.
-- Flyway가 검증하기 전에 매번 실행된다. 이력 테이블이 없거나(새 DB) 이미 지웠으면 아무것도 하지 않는다.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        DELETE FROM public.flyway_schema_history
        WHERE script IN ('product/V202610292246__product_outbox.sql',
                         'product/V202611040911__drop_product_search_document.sql',
                         'product/V202611041223__product_outbox_if_missing.sql');
    END IF;
END
$$;
