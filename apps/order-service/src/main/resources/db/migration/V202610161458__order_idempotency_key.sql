-- #351 주문 요청 멱등성: 같은 회원의 같은 요청 키는 주문 하나.
-- 키 없이 만든 주문(기존 주문 포함)은 두 칼럼이 null이고, PostgreSQL 유니크는 null끼리 겹쳐도 된다.
ALTER TABLE orders.orders
    ADD COLUMN idempotency_key character varying(36),
    ADD COLUMN request_hash character varying(64);

ALTER TABLE ONLY orders.orders
    ADD CONSTRAINT uk_orders_member_idempotency_key UNIQUE (member_id, idempotency_key);
