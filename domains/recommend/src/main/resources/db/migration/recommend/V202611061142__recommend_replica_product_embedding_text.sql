-- 이 칸이 생기기 전에 받은 상품은 null이고, 그 상품의 다음 product.changed나 다시 보내기로 채워진다
ALTER TABLE recommend.replica_product ADD COLUMN embedding_text text;
