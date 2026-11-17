-- #1328 상품 썸네일: 상품 복사본(product.changed의 thumbnailUrl)과 주문상품 스냅샷(주문 시점 복사본 값)에 칸을 더한다.
-- 상품 쪽 product.thumbnail_url과 같은 길이. 비어도 되고 기존 행은 채우지 않는다(null). 두 번 적용해도 안전하다
ALTER TABLE orders.replica_product ADD COLUMN IF NOT EXISTS thumbnail_url character varying(255);
ALTER TABLE orders.order_item ADD COLUMN IF NOT EXISTS thumbnail_url character varying(255);
