-- #968 조르기 철회: 상태에 CANCELLED를 더하고 철회 시각 칸을 둔다(철회한 사람은 보낸 사람이라 따로 두지 않는다).
-- baseline 뒤 파일이라 이미 있는 DB에 다시 적용돼도 안전하게 둔다(OrderMigrationIntegrationTest)
ALTER TABLE orders.wish_request DROP CONSTRAINT IF EXISTS wish_request_status_check;
ALTER TABLE orders.wish_request ADD CONSTRAINT wish_request_status_check CHECK (((status)::text = ANY
    ((ARRAY['REQUESTED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying,
        'CANCELLED'::character varying])::text[])));
ALTER TABLE orders.wish_request ADD COLUMN IF NOT EXISTS cancelled_at timestamp(6) without time zone;
