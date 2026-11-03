-- kidly-app의 같은 버전 파일에서 payment 스키마 구문만 가져왔다(#525). 내용을 고치지 않는다
-- Flyway 도입 전 Hibernate ddl update로 만든 서버 DB(dev)를 V1과 같게 맞춘다 (#375).
-- V1부터 만든 DB(prod 첫 배포 · 테스트)에는 해당 차이가 없으므로 아무것도 바꾸지 않는다

-- 주문 취소 환불은 충전 취소가 아니라 cancellation_id를 비워 저장한다 (#273)
ALTER TABLE payment.refund ALTER COLUMN cancellation_id DROP NOT NULL;
