-- #939 같은 회원 · 자녀(자녀를 고르지 않으면 회원) · 상품의 상세 진입은 같은 날 한 번만 남긴다.
-- 상세에서 새로 고칠 때마다 한 줄씩 쌓이면 한 상품이 자녀의 취향 계산에서 무게를 독차지하기 때문이다.
-- 날짜는 occurred_at의 날짜다. 이 칸은 한국 시간으로 저장하므로 ::date도 한국 날짜다.

-- 색인을 만들려면 이미 쌓인 중복이 없어야 하므로, 같은 날의 같은 상세 진입은 id가 가장 작은 한 줄만 남긴다.
-- 자녀를 고르지 않은 줄(child_profile_id가 null)끼리도 같은 활동이라 IS NOT DISTINCT FROM으로 비교한다.
DELETE FROM recommend.activity_record later
WHERE later.type = 'PRODUCT_VIEWED'
  AND EXISTS (
      SELECT 1
      FROM recommend.activity_record earlier
      WHERE earlier.type = 'PRODUCT_VIEWED'
        AND earlier.member_id = later.member_id
        AND earlier.child_profile_id IS NOT DISTINCT FROM later.child_profile_id
        AND earlier.product_id = later.product_id
        AND earlier.occurred_at::date = later.occurred_at::date
        AND earlier.id < later.id);

-- 상세 진입 저장은 INSERT ... ON CONFLICT DO NOTHING이 이 색인과 부딪히면 건너뛴다.
-- 다른 활동 종류(검색 · 장바구니 담기 · 빼기)는 같은 날 같은 상품을 여러 번 남길 수 있어 WHERE 조건으로 색인에서 뺀다.
-- 자녀를 고르지 않은 줄도 같은 줄로 막으려고 null을 서로 같은 값으로 본다(NULLS NOT DISTINCT).
CREATE UNIQUE INDEX idx_activity_record_product_viewed_per_day
    ON recommend.activity_record (member_id, child_profile_id, product_id, (occurred_at::date)) NULLS NOT DISTINCT
    WHERE type = 'PRODUCT_VIEWED';
