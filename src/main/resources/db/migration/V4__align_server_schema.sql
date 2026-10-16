-- Flyway 도입 전 Hibernate ddl update로 만든 서버 DB(dev)를 V1과 같게 맞춘다 (#375).
-- update는 컬럼을 지우거나 NOT NULL을 풀지 않아 엔티티가 바뀐 뒤에도 옛 제약이 남았다.
-- V1부터 만든 DB(prod 첫 배포 · 테스트)에는 해당 차이가 없으므로 모두 IF EXISTS로 써서 아무것도 바꾸지 않는다

-- 주문 취소 환불은 충전 취소가 아니라 cancellation_id를 비워 저장한다 (#273)
ALTER TABLE payment.refund ALTER COLUMN cancellation_id DROP NOT NULL;

-- 정산 대상에서 빠진 옛 컬럼. eligible_at이 NOT NULL로 남아 있으면 정산 대상 등록이 실패한다
ALTER TABLE settlement.settlement_item DROP COLUMN IF EXISTS eligible_at;
ALTER TABLE settlement.settlement_item DROP COLUMN IF EXISTS fee_amount;
ALTER TABLE settlement.settlement_item DROP COLUMN IF EXISTS settlement_amount;
ALTER TABLE settlement.settlement_item DROP COLUMN IF EXISTS settlement_statement_id;

-- 정산서는 settlement.settlement로 바뀌었다. 옛 테이블은 쓰지 않는다
DROP TABLE IF EXISTS settlement.settlement_statement;
