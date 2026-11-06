-- #999 조르기를 구매 요청으로 다시 만든다(승인 = 결제). 조르기는 장바구니 칸을 예약하고, 보호자가 그 칸으로 주문해 결제가 끝나면
-- 장바구니 구매 이벤트를 받아 결제 완료가 된다. 공용 장바구니(wish_item)와 승인 단계(바로 결제 · 보호자 장바구니로 옮기기)를 없앤다.
-- 데이터(참모 결정 ③): 진행 중 조르기(대기 · 승인 뒤 결과 없음 · 장바구니로 옮김)는 철회(원인 SYSTEM)로, 바로 결제가 결제된 조르기는
-- 결제 완료로, 거절 · 철회는 그대로 옮긴다. wish_item 표와 그 표를 가리키던 칸(wish_request_line.wish_item_id)은 지운다.
-- baseline 뒤 파일이라 이미 있는 DB에 다시 적용돼도 안전하게 둔다(OrderMigrationIntegrationTest): 칸 · 제약은 IF (NOT) EXISTS,
-- 지울 칸을 읽는 옮기기는 그 칸이 있을 때만 한다.

-- 1. 새 칸
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS type character varying(20);
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS payer_type character varying(20);
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS payer_id bigint;
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS paid_at timestamp(6) without time zone;
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS rejected_by_type character varying(20);
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS rejected_by_id bigint;
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS rejected_at timestamp(6) without time zone;
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS cancel_reason character varying(30);

-- 2. 상태 · 보낸 사람 종류 제약은 값을 옮기기 전에 푼다
ALTER TABLE orders.wish_request DROP CONSTRAINT IF EXISTS wish_request_status_check;
ALTER TABLE orders.wish_request DROP CONSTRAINT IF EXISTS wish_request_requested_by_type_check;

-- 3. 값 옮기기. 결정 칸(decided_*) · 바로 결제 칸(checkout · order_result)이 남아 있을 때(처음 적용)만.
-- 진행 중 조르기 철회도 여기서만 한다: 다시 적용될 때 새 모델의 대기(REQUESTED) 조르기를 건드리지 않게
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'orders' AND table_name = 'wish_request' AND column_name = 'decided_by_id') THEN
        UPDATE orders.wish_request
        SET status = 'PAID', payer_type = 'MEMBER', payer_id = decided_by_id, paid_at = decided_at
        WHERE status = 'APPROVED' AND checkout = 'ORDER' AND order_result = 'PAID';

        UPDATE orders.wish_request
        SET rejected_by_type = 'MEMBER', rejected_by_id = decided_by_id, rejected_at = decided_at
        WHERE status = 'REJECTED' AND rejected_by_id IS NULL;

        UPDATE orders.wish_request
        SET status = 'CANCELLED', cancel_reason = 'SYSTEM', cancelled_at = COALESCE(cancelled_at, now())
        WHERE status IN ('REQUESTED', 'APPROVED');
    END IF;
END
$$;

UPDATE orders.wish_request SET cancel_reason = 'DIRECT' WHERE status = 'CANCELLED' AND cancel_reason IS NULL;
UPDATE orders.wish_request SET requested_by_type = 'MEMBER' WHERE requested_by_type = 'PARENT';
UPDATE orders.wish_request SET type = 'WISH' WHERE type IS NULL;
ALTER TABLE orders.wish_request ALTER COLUMN type SET NOT NULL;

-- 4. 새 제약
ALTER TABLE orders.wish_request ADD CONSTRAINT wish_request_status_check CHECK (((status)::text = ANY
    ((ARRAY['REQUESTED'::character varying, 'PAID'::character varying, 'REJECTED'::character varying,
        'CANCELLED'::character varying])::text[])));
ALTER TABLE orders.wish_request ADD CONSTRAINT wish_request_requested_by_type_check CHECK (((requested_by_type)::text
    = ANY ((ARRAY['MEMBER'::character varying, 'CHILD'::character varying])::text[])));
ALTER TABLE orders.wish_request DROP CONSTRAINT IF EXISTS wish_request_type_check;
ALTER TABLE orders.wish_request ADD CONSTRAINT wish_request_type_check CHECK (((type)::text = ANY
    ((ARRAY['WISH'::character varying])::text[])));
ALTER TABLE orders.wish_request DROP CONSTRAINT IF EXISTS wish_request_payer_type_check;
ALTER TABLE orders.wish_request ADD CONSTRAINT wish_request_payer_type_check CHECK (((payer_type)::text = ANY
    ((ARRAY['MEMBER'::character varying, 'CHILD'::character varying])::text[])));
ALTER TABLE orders.wish_request DROP CONSTRAINT IF EXISTS wish_request_rejected_by_type_check;
ALTER TABLE orders.wish_request ADD CONSTRAINT wish_request_rejected_by_type_check CHECK (((rejected_by_type)::text
    = ANY ((ARRAY['MEMBER'::character varying, 'CHILD'::character varying])::text[])));
ALTER TABLE orders.wish_request DROP CONSTRAINT IF EXISTS wish_request_cancel_reason_check;
ALTER TABLE orders.wish_request ADD CONSTRAINT wish_request_cancel_reason_check CHECK (((cancel_reason)::text = ANY
    ((ARRAY['DIRECT'::character varying, 'CHILD_DELETED'::character varying, 'ACCOUNT_DELETED'::character varying,
        'SYSTEM'::character varying])::text[])));

-- 5. 없앤 승인 단계의 칸(결정 · 바로 결제 · 장바구니로 옮기기)
ALTER TABLE orders.wish_request DROP COLUMN IF EXISTS checkout;
ALTER TABLE orders.wish_request DROP COLUMN IF EXISTS order_id;
ALTER TABLE orders.wish_request DROP COLUMN IF EXISTS order_result;
ALTER TABLE orders.wish_request DROP COLUMN IF EXISTS decided_by_id;
ALTER TABLE orders.wish_request DROP COLUMN IF EXISTS decided_at;

-- 보낸 사람별 목록(요청자 · 승인자가 보는 목록, 최근부터)
CREATE INDEX IF NOT EXISTS idx_wish_request_requested_by
    ON orders.wish_request USING btree (requested_by_type, requested_by_id, id);

-- 6. 조르기 줄: 공용 장바구니 줄 대신 장바구니 칸(예약한 칸)과 보낸 때 상품 값. 예전 줄은 칸이 없다(null)
ALTER TABLE orders.wish_request_line ADD COLUMN IF NOT EXISTS cart_item_id bigint;
ALTER TABLE orders.wish_request_line ADD COLUMN IF NOT EXISTS product_id bigint;
ALTER TABLE orders.wish_request_line DROP COLUMN IF EXISTS wish_item_id;
ALTER TABLE orders.wish_request_line DROP COLUMN IF EXISTS approved_quantity;
-- 진행 중인 조르기에 든 칸 찾기(BEG-02)
CREATE INDEX IF NOT EXISTS idx_wish_request_line_cart_item ON orders.wish_request_line USING btree (cart_item_id);

-- 7. 공용 장바구니
DROP TABLE IF EXISTS orders.wish_item;

-- 8. 조르기로 산 것(#1000이 출처 표시에 쓴다): 결제가 끝나 빠진 장바구니 칸 하나 = 한 행. 주문상품은 order_item_id
-- (cart.item-purchased 값, 없으면 null)로, 그 밖에는 (주문 ID, 장바구니 칸 ID)로 맞춘다(order_item.cart_item_id, #998).
-- 주문 FK는 두지 않는다(다른 컨텍스트 값)
CREATE TABLE IF NOT EXISTS orders.wish_purchase (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    request_id bigint NOT NULL,
    order_id bigint NOT NULL,
    order_item_id bigint,
    cart_item_id bigint NOT NULL,
    option_id bigint NOT NULL,
    quantity integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT uk_wish_purchase_request_cart_item UNIQUE (request_id, cart_item_id),
    CONSTRAINT wish_purchase_quantity_check CHECK (quantity >= 1),
    CONSTRAINT fk_wish_purchase_request FOREIGN KEY (request_id) REFERENCES orders.wish_request (id)
);

-- 결제된 주문상품 ID(cart.item-purchased의 orderItemId). 표가 이 칸 없이 먼저 만들어졌어도 더한다(두 번 적용 안전)
ALTER TABLE orders.wish_purchase ADD COLUMN IF NOT EXISTS order_item_id bigint;

CREATE INDEX IF NOT EXISTS idx_wish_purchase_order ON orders.wish_purchase USING btree (order_id, cart_item_id);
