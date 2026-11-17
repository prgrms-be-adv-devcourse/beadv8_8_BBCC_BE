-- 지난번 확인 뒤에 고쳐진 상품 복사본만 찾으려고 수정 시각 색인을 더한다.
CREATE INDEX idx_replica_product_updated_at ON recommend.replica_product (updated_at);
