-- #1008 조르기 기한 만료(BEG-05): 상태에 EXPIRED를 더하고 만료 처리 시각 칸을 둔다. 만료 기한은 저장하지 않는다
-- (보낸 시각 + 지금 설정 기한으로 계산, kidly.order.wish.expiry.ttl). 만료한 주체는 시스템이라 따로 두지 않는다.
-- baseline 뒤 파일이라 이미 있는 DB에 다시 적용돼도 안전하게 둔다(OrderMigrationIntegrationTest)
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS expired_at timestamp(6) without time zone;

ALTER TABLE orders.wish_request DROP CONSTRAINT IF EXISTS wish_request_status_check;
ALTER TABLE orders.wish_request ADD CONSTRAINT wish_request_status_check CHECK (((status)::text = ANY
    ((ARRAY['REQUESTED'::character varying, 'PAID'::character varying, 'REJECTED'::character varying,
        'CANCELLED'::character varying, 'EXPIRED'::character varying])::text[])));

-- 기한 지난 대기 조르기 찾기(주기 작업, 보낸 시각 기준 ID 커서)
CREATE INDEX IF NOT EXISTS idx_wish_request_status_created ON orders.wish_request USING btree (status, created_at, id);
