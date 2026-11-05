-- 상품이 등록 취소 상태를 보내기 전에 복사본이 그 값을 저장할 수 있게 한다.
ALTER TABLE recommend.replica_product DROP CONSTRAINT IF EXISTS replica_product_status_check;
ALTER TABLE recommend.replica_product ADD CONSTRAINT replica_product_status_check
    CHECK (status IN ('SALE_PENDING', 'ON_SALE', 'STOPPED', 'DELISTED'));
