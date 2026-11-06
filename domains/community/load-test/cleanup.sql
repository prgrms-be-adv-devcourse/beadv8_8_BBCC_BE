-- 커뮤니티 동시성 부하 측정(#923) 데이터 지우기. seed.sql이 넣은 행과 측정이 만든 행만 지운다
-- 측정이 만드는 찜 · 리뷰 · 집계는 늘 측정 상품(9000001~9000004) 안이므로 상품 범위로만 지운다
-- (회원 범위로 넓히면 그 회원이 다른 상품에 남긴 행까지 지워져 그 상품의 집계와 어긋날 수 있다)
-- 작성 자격은 시드한 주문상품(9300001, 9400001~9400200)만 지운다. 다시 돌려도 안전하다
BEGIN;

DELETE FROM community.favorite WHERE product_id BETWEEN 9000001 AND 9000004;
DELETE FROM community.favorite_stats WHERE product_id BETWEEN 9000001 AND 9000004;

DELETE FROM community.review WHERE product_id BETWEEN 9000001 AND 9000004;
DELETE FROM community.review_stats WHERE product_id BETWEEN 9000001 AND 9000004;

DELETE FROM community.review_eligibility
WHERE order_item_id = 9300001 OR order_item_id BETWEEN 9400001 AND 9400200;
DELETE FROM community.replica_product WHERE product_id BETWEEN 9000001 AND 9000004;

COMMIT;
