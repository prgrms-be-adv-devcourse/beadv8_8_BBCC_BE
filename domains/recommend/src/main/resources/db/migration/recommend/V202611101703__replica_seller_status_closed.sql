-- 판매자 도메인이 탈퇴한 판매자의 상태(CLOSED)를 보내기 전에 판매자 복사본이 그 값을 저장할 수 있게 한다.
ALTER TABLE recommend.replica_seller DROP CONSTRAINT IF EXISTS replica_seller_status_check;
ALTER TABLE recommend.replica_seller ADD CONSTRAINT replica_seller_status_check
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'));
