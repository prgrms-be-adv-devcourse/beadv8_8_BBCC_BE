-- 주문 · 커뮤니티 도메인 모듈의 마이그레이션(orders/ 8개 · community/ 6개)은 주문 서비스(#826) · 커뮤니티 서비스(#827)로 옮겨 이 앱의 클래스패스에 없다.
-- 이 앱이 적용한 이력 행만 남아 검증이 missing 으로 실패하므로 지운다. 이 앱 모듈에 있는 주문 파일(init · 옛 주문 파일 5개)은
-- 이미 적용된 파일이라 그대로 두고 이 앱이 계속 가진다. 커뮤니티 파일은 이 앱 모듈에 없다.
-- 서비스는 자기 이력 테이블(orders.flyway_schema_history_order · community.flyway_schema_history_community)을 쓰므로 이 행을 보지 않는다.
-- Flyway가 검증하기 전에 매번 실행된다. 이력 테이블이 없거나(새 DB) 이미 지웠으면 아무것도 하지 않는다.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        DELETE FROM public.flyway_schema_history
        WHERE script IN ('orders/V202610292033__order_outbox.sql',
                         'orders/V202610310156__order_dead_letter.sql',
                         'orders/V202611010239__wish_item.sql',
                         'orders/V202611011756__order_delivery_tracking_stopped_at.sql',
                         'orders/V202611020031__order_item_seller_cancel.sql',
                         'orders/V202611021347__wish_request.sql',
                         'orders/V202611031010__order_child_deletion_tombstone.sql',
                         'orders/V202611031100__order_outbox_quarantine.sql',
                         'community/V202611020103__community_schema.sql',
                         'community/V202611020132__community_replica_product.sql',
                         'community/V202611021048__community_review_eligibility.sql',
                         'community/V202611021140__community_review.sql',
                         'community/V202611021458__community_favorite.sql',
                         'community/V202611031200__community_dead_letter.sql');
    END IF;
END
$$;
