-- #1121 판매자가 판매할 수 없는 상태(ACTIVE 아님)가 되면 배송 전 주문을 정리한다: 취소 사유 값 SELLER_UNAVAILABLE을 더한다.
-- 사유 칸의 허용 값 제약만 바꾼다(칸 · 데이터 그대로). 지우고 다시 만들어 두 번 적용해도 안전하다
ALTER TABLE orders.orders DROP CONSTRAINT IF EXISTS orders_cancel_reason_check;
ALTER TABLE orders.orders ADD CONSTRAINT orders_cancel_reason_check CHECK (((cancel_reason)::text = ANY ((ARRAY[
    'OUT_OF_STOCK'::character varying, 'PAYMENT_FAILED'::character varying, 'PAYMENT_EXPIRED'::character varying,
    'BUYER_CANCELLED'::character varying, 'SELLER_CANCELLED'::character varying,
    'SELLER_UNAVAILABLE'::character varying])::text[])));

ALTER TABLE orders.order_item DROP CONSTRAINT IF EXISTS order_item_cancel_reason_check;
ALTER TABLE orders.order_item ADD CONSTRAINT order_item_cancel_reason_check CHECK (((cancel_reason)::text = ANY ((ARRAY[
    'OUT_OF_STOCK'::character varying, 'WRONG_PRODUCT_INFO'::character varying, 'OTHER'::character varying,
    'SELLER_UNAVAILABLE'::character varying])::text[])));

ALTER TABLE orders.order_item_cancellation DROP CONSTRAINT IF EXISTS order_item_cancellation_reason_check;
ALTER TABLE orders.order_item_cancellation ADD CONSTRAINT order_item_cancellation_reason_check CHECK (((reason)::text
    = ANY ((ARRAY['OUT_OF_STOCK'::character varying, 'WRONG_PRODUCT_INFO'::character varying,
    'OTHER'::character varying, 'SELLER_UNAVAILABLE'::character varying])::text[])));
