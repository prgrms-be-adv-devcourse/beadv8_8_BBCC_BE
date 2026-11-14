-- #1363 같은 회원 · 자녀(자녀를 고르지 않으면 회원) · 상품의 팝업 조회는 같은 날 한 번만 남긴다.
-- 같은 상품의 담기 팝업을 여러 번 열 때마다 한 줄씩 쌓이면 한 상품이 자녀의 취향 계산에서 무게를 독차지하기 때문이다.
-- 날짜는 occurred_at의 날짜다. 이 칸은 한국 시간으로 저장하므로 ::date도 한국 날짜다.

-- 팝업 조회 기록은 이 마이그레이션 전에는 없으므로 쌓인 중복을 지우지 않는다.
-- 상세 진입과 검색 결과 클릭은 각자의 색인으로 하루 한 번을 지킨다.
-- 같은 날 같은 상품의 팝업 조회와 상세 진입은 둘 다 남기므로 이 색인에는 팝업 조회만 담는다.
CREATE UNIQUE INDEX idx_activity_record_product_quick_viewed_per_day
    ON recommend.activity_record (member_id, child_profile_id, product_id, (occurred_at::date)) NULLS NOT DISTINCT
    WHERE type = 'PRODUCT_QUICK_VIEWED';
