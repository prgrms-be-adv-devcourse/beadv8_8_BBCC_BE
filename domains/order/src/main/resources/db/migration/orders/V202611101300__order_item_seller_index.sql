-- #1120 판매자 미종결 주문상품 수(GET /internal/sellers/{sellerId}/order-items/summary)를 건수 쿼리 한 번으로 센다.
-- 주문상품에 판매자 ID 인덱스가 없어 (판매자, 상태)로 더한다. 조회용 인덱스만이고 데이터는 바꾸지 않는다. 두 번 적용해도 안전하다
CREATE INDEX IF NOT EXISTS idx_order_item_seller_status ON orders.order_item USING btree (seller_id, status);
