-- #1000 자녀 구매 내역: 입을 아이(child_id)로 주문상품을 찾는다. 자녀를 고르지 않은 주문상품(null)은 넣지 않는다.
-- baseline 뒤 파일이라 이미 있는 DB에 다시 적용돼도 안전하게 둔다
CREATE INDEX IF NOT EXISTS idx_order_item_child ON orders.order_item USING btree (child_id, order_id)
    WHERE child_id IS NOT NULL;
