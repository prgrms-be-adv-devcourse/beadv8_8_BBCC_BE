-- kidly-app의 같은 버전 파일에서 settlement 구문만 뽑았다(#729). 내용을 고치지 않는다

-- 정산 대상에서 빠진 옛 컬럼. eligible_at이 NOT NULL로 남아 있으면 정산 대상 등록이 실패한다
ALTER TABLE settlement.settlement_item DROP COLUMN IF EXISTS eligible_at;
ALTER TABLE settlement.settlement_item DROP COLUMN IF EXISTS fee_amount;
ALTER TABLE settlement.settlement_item DROP COLUMN IF EXISTS settlement_amount;
ALTER TABLE settlement.settlement_item DROP COLUMN IF EXISTS settlement_statement_id;

-- 정산서는 settlement.settlement로 바뀌었다. 옛 테이블은 쓰지 않는다
DROP TABLE IF EXISTS settlement.settlement_statement;
