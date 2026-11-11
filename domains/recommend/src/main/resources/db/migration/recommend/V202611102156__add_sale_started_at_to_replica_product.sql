-- 상품이 처음 판매중이 된 시각이다. 칸이 없는 예전 메시지로 만든 복사본은 비어 있다
ALTER TABLE recommend.replica_product ADD COLUMN sale_started_at timestamp(6) without time zone;
