-- 커뮤니티 동시성 부하 측정(#923) 준비 데이터. 로컬 community 스키마에만 넣는다(운영 · dev DB에 넣지 않는다)
-- ID는 실데이터와 섞이지 않게 큰 고정 범위를 쓴다(README "ID 범위"):
--   상품 9000001~9000004, 작성 자격 주문상품 9300001 · 9400001~9400200(회원 ID와 같은 번호)
--   cleanup.sql도 이 범위만 지운다(찜 · 리뷰 · 집계는 상품 범위로)
-- 다시 돌려도 안전하다(이미 있으면 건너뛴다). 측정 전에는 cleanup.sql을 먼저 돌려 찜 · 리뷰를 비운다
BEGIN;

-- 상품 복사본(판매 중). 찜은 복사본에 있는 판매 중 상품만 받는다
INSERT INTO community.replica_product
    (product_id, name, thumbnail_url, display_status, source_occurred_at, created_at, updated_at)
VALUES
    (9000001, 'LOADTEST 찜 · 서로 다른 회원', NULL, 'ON_SALE', '2026-01-01 00:00:00', now(), now()),
    (9000002, 'LOADTEST 찜 · 같은 회원', NULL, 'ON_SALE', '2026-01-01 00:00:00', now(), now()),
    (9000003, 'LOADTEST 리뷰 · 같은 주문상품', NULL, 'ON_SALE', '2026-01-01 00:00:00', now(), now()),
    (9000004, 'LOADTEST 리뷰 · 서로 다른 회원', NULL, 'ON_SALE', '2026-01-01 00:00:00', now(), now())
ON CONFLICT (product_id) DO NOTHING;

-- 시나리오 3: 주문상품 하나(9300001)의 작성 자격. 회원 9300001
INSERT INTO community.review_eligibility
    (order_item_id, order_id, member_id, product_id, option_id, size, confirmed_at, created_at, updated_at)
VALUES (9300001, 9300001, 9300001, 9000003, NULL, NULL, '2026-01-01 00:00:00', now(), now())
ON CONFLICT (order_item_id) DO NOTHING;

-- 시나리오 4: 회원 9400001~9400200이 상품 9000004를 하나씩 구매확정(주문상품 ID = 회원 ID). N은 200까지
INSERT INTO community.review_eligibility
    (order_item_id, order_id, member_id, product_id, option_id, size, confirmed_at, created_at, updated_at)
SELECT 9400000 + i, 9400000 + i, 9400000 + i, 9000004, NULL, NULL, '2026-01-01 00:00:00', now(), now()
FROM generate_series(1, 200) AS i
ON CONFLICT (order_item_id) DO NOTHING;

COMMIT;
