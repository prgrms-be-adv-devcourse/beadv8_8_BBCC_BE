-- #354 구매자 배송 전 전체 취소.
-- 배송: 발송과 취소가 동시에 오면 한쪽만 성공하도록 버전 칼럼을 둔다(기존 행은 0부터). 상태에 CANCELLED를 더한다.
-- 주문: 취소 사유에 BUYER_CANCELLED를 더한다. 제약 이름은 V1과 같고, 다시 만들면서 값만 늘린다.
ALTER TABLE orders.delivery
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE orders.delivery DROP CONSTRAINT delivery_status_check;

ALTER TABLE orders.delivery
    ADD CONSTRAINT delivery_status_check CHECK (((status)::text = ANY ((ARRAY['READY'::character varying,
        'SHIPPING'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[])));

ALTER TABLE orders.orders DROP CONSTRAINT orders_cancel_reason_check;

ALTER TABLE orders.orders
    ADD CONSTRAINT orders_cancel_reason_check CHECK (((cancel_reason)::text = ANY ((ARRAY[
        'OUT_OF_STOCK'::character varying, 'PAYMENT_FAILED'::character varying, 'PAYMENT_EXPIRED'::character varying,
        'BUYER_CANCELLED'::character varying])::text[])));
