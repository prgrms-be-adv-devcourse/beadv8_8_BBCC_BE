-- 정산을 영업일마다 하루 1회 실행한다(#679). 정산서 시작일은 포함한 항목 중 가장 이른 확정일이라 실행마다 달라질 수 있으므로
-- 한 실행(종료일)에 판매자의 정산서가 하나만 생기도록 유일 키를 (판매자, 종료일)로 바꾼다.
-- 기존 행은 시작일이 종료일로 정해졌으므로(종료일이 속한 달 1일) 판매자 · 종료일마다 하나라 새 키와 겹치지 않는다.
ALTER TABLE settlement.settlement DROP CONSTRAINT uk_settlement_seller_period;

ALTER TABLE settlement.settlement
    ADD CONSTRAINT uk_settlement_seller_period_end_on UNIQUE (seller_id, period_end_on);
