-- 구매확정 뒤에는 환불이 없어 정산 항목을 제외할 일이 없다. 쓰이지 않는 EXCLUDED 상태를 없앤다 (#484)
ALTER TABLE settlement.settlement_item DROP CONSTRAINT settlement_item_status_check;
ALTER TABLE settlement.settlement_item ADD CONSTRAINT settlement_item_status_check
    CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SETTLED'::character varying])::text[])));
